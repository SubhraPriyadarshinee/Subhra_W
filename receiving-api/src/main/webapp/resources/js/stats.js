const contentType = "application/json";
const dataType = "json";
var todatetime = 0;
var fromdatetime = 0;
var isUTC ;

function getDateTimeInUtc() {
	var hours = document.getElementById("timeFrameFormSelect").value;

	if (hours !== "s") {
        var tmLoc = new Date();
        todatetime = tmLoc.getTime() + tmLoc.getTimezoneOffset() * 60000
        fromdatetime =todatetime -(hours * 60 * 60 *1000);

        isUTC= true;
        console.log("drop_UTCStart_date="+fromdatetime);
        console.log("drop_UTCend_date="+todatetime);
    }
	else {
	    todatetime = 0;
	    fromdatetime = 0;
	    console.log("Nothing selected in time frame drop down");
    }
}
$(function() {
	$("#datetimes").daterangepicker({
        timePicker: true,
        autoUpdateInput: false,
        locale: {
            cancelLabel: 'Clear'
        }
    });
    $("#datetimes").on('apply.daterangepicker', function(ev, picker) {
        $("#timeFrameFormSelect").val('s').change();
        now = new Date();
        isUTC= false;
	    fromdatetime =(picker.startDate._d).getTime();
	    todatetime = (picker.endDate._d).getTime();
        $(this).val(picker.startDate.format('DD/M/YY hh:mm A') + ' - ' + picker.endDate.format('DD/M/YY hh:mm A'));
	    console.log("Start date:" + fromdatetime + " End date:" + todatetime + "Current date:" + now.getTime());
    });
    $("#datetimes").on('cancel.daterangepicker', function(ev, picker) {
        todatetime = 0;
        fromdatetime = 0;
        $(this).val('');
    });
    $("#timeFrameFormSelect").change(function () {
        $("#datetimes").val('');
    });
});
$(document).ready(function () {
    $("#ajax_loader").hide();
    $("#error").css({
        "display": "none"
    });
    $("#success").css({
        "display": "none"
    });
    

    $("#facilityForm").submit(function (event) {
        
        console.log("fromdatetime="+fromdatetime);
        console.log("todatetime="+todatetime);
        event.preventDefault();
        if (fromdatetime === 0) {
            $("#error").css({
                "display": "block"
            });
            $("#error").html("Please enter a valid time range");
            return;
        }
        $("#tableBody").hide();
        $("#tableBody2").hide();

        $("#ajax_loader").show();
        $.ajax({
          type: 'GET',
            url: "/report/stats/data",
            data: {
            	todatetime: todatetime,
            	fromdatetime: fromdatetime,
             	isUTC :isUTC
            },
            dataType: dataType,
            contentType: contentType,
            headers: {
                "WMT-UserId": $("#userId").val(),
                "facilityNum": $("#facilityNumFormSelect").val(),
                "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
            },
            error: function (jqXHR, textStatus, errorThrown) {
                $("#tableBody").empty();
                $("#tableBody2").empty();
                console.log(jqXHR.responseText);
                $("#error").css({
                    "display": "block"
                });
                $("#error").html("Something went wrong");
                $("#ajax_loader").hide();
            },
            success: function (data, textStatus, jqXHR) {
                console.log(JSON.stringify(data));
                $("#error").css({
                    "display": "none"
                });
                $("#tableBody").show();
                $("#tableBody").empty();
                $("#tableBody2").show();
                $("#tableBody2").empty();

                let i = 0;
                let maxRowCount = 16;
                $.each(data, function (index, value) {
                    let tableRow = "<tr><td>" + value.key + "</td><td>" +
                        value.value + "</td></tr>";
                    if (i < maxRowCount)
                        $("#tableBody").append(tableRow);
                    else
                        $("#tableBody2").append(tableRow);
                    i++;
                });

                $("#ajax_loader").hide();
            }
        });
    });
});