let po;
let poLine;
let delivery;
let instructionId;
let selectedSearch = 'delivery';
let instructionData;
let containerData;

const contentType = "application/json";
const dataType = "json";
function HtmlEncode(s) {
    var htmlCharMap  = {
        "'" : "&#39",
        "<" : "&lt",
        ">" : "&gt",
        "`" : "&#x60",
    };
    function encode(ch) {
        return htmlCharMap[ch]
    }
    return s.replace(/['<>`]/g, encode)
}
let deliverySearchFormFunc = () => {

    delivery = $("#searchField").val();

    let headers = {
        "facilityNum": $("#facilityNumFormSelect").val(),
        "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
        "WMT-UserId": "report-user"
    };

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/report/search/delivery/" + delivery,
        headers: headers,
        dataType: dataType,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(jqXHR.responseText);
            $("#error").css({
                "display": "block"
            });
            $("#error").text(textStatus + " " + jqXHR.responseText);
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data));
            $("#error").css({
                "display": "none"
            });
            $("#deliveryDataSection").show();
            $("#deliveryTableBody").empty();
            $("#deliveryTitle").empty();
            $("#deliveryStatus").empty();


            let showOrderedLabelsHeader = false;
            let showOverageLabelsHeader = false;
            let showExceptionLabelsHeader = false;


            let deliveryTitle = "<h4>Delivery Number #" + delivery + "</h4>";
            let doorNumber = "<div class='col-lg-4 float-left'>" +
                "<label for='doorNumberText'>Door Number</label>" +
                "<p class='text-left' id='doorNumberText' class='col-lg-3'>"
                                +HtmlEncode(data.doorNumber) +"</p></div>";
            let deliveryStatus = "<div class='col-lg-4 float-left'>" +
                "<label for='deliveryStatusText'>Delivery Status</label>" +
                "<p class='text-left' id='deliveryStatusText' class='col-lg-3'>"
                                + HtmlEncode(data.deliveryStatus) +"</p></div>";

            $("#orderedLabelsHeader").hide();
            $("#overageLabelsHeader").hide();
            $("#exceptionLabelsHeader").hide();


            $.each(data.deliveryDocuments, function (index, value) {
                $.each(value.deliveryDocumentLines, function(ind, val) {
                    let checkboxId= index.toString() + "_" +  ind.toString();
                    let deliveryData = "<tr><td><input id="
                        + HtmlEncode (checkboxId) + " type='radio' name='optradiod' "
                        + "onclick='poPoLineCheckboxFunc(this.checked, this.id)'/> <td id='po_"
                        + HtmlEncode (checkboxId) + "'>"
                        + HtmlEncode(val.purchaseReferenceNumber) + "</td><td id='po_line_"
                        + HtmlEncode(checkboxId) + "'>"
                        + HtmlEncode( val.purchaseReferenceLineNumber) + "</td><td>"
                        + HtmlEncode(val.itemNbr) + "</td><td>"
                        + HtmlEncode(val.itemUPC) + "</td><td>"
                        + HtmlEncode(val.purchaseRefType) + "</td>"
                        + (typeof val.orderedLabelCount === 'undefined' ? "" : "<td>" + HtmlEncode(val.orderedLabelCount) + "</td>")
                        + (typeof val.overageLabelCount === 'undefined' ? "" : "<td>" + HtmlEncode(val.overageLabelCount) + "</td>")
                        + (typeof val.exceptionLabelCount === 'undefined' ? "" : "<td>" + HtmlEncode(val.exceptionLabelCount) + "</td>")
                        + "</tr>";

                    showOrderedLabelsHeader = showOrderedLabelsHeader || typeof val.orderedLabelCount !== 'undefined';
                    showOverageLabelsHeader = showOverageLabelsHeader || typeof val.orderedLabelCount !== 'undefined';
                    showExceptionLabelsHeader = showExceptionLabelsHeader || typeof val.orderedLabelCount !== 'undefined';

                    $("#deliveryTableBody").append(deliveryData);
                });
            });

            if (showOrderedLabelsHeader) {
                $("#orderedLabelsHeader").show();
            }
            if (showOverageLabelsHeader) {
                $("#overageLabelsHeader").show();
            }
            if (showExceptionLabelsHeader) {
                $("#exceptionLabelsHeader").show();
            }

            $("#deliveryStatus").append(doorNumber);
            $("#deliveryStatus").append(deliveryStatus);
            $("#deliveryTitle").append(deliveryTitle);

            $("#ajax_loader").hide();
        }
    });
};

let containerSearchFunc = () => {

    let headers = {
        "facilityNum": $("#facilityNumFormSelect").val(),
        "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
        "WMT-UserId": "report-user"
    };

    $("#containerDetailsSection").show();
    $("#containerDetailsBackButton").hide();
    $("#containerDetails").text(null);

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/recon/container/bytrackingid/" + $("#searchField").val(),
        dataType: dataType,
        headers: headers,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(jqXHR.responseText);
            $("#error").css({
                "display": "block"
            });
            $("#error").text(textStatus + " " + jqXHR.responseText);
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data));
            $("#error").css({
                "display": "none"
            });

            let jsonPretty = JSON.stringify(data, null, '\t');

            $("#containerDetails").text(jsonPretty);

            $("#ajax_loader").hide();
        }
    });
};

let instructionSearchFunc = () => {

    let headers = {
        "facilityNum": $("#facilityNumFormSelect").val(),
        "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
        "WMT-UserId": "report-user"
    };

    $("#instructionDetailsSection").show();
    $("#instructionDetailsBackButton").hide();
    $("#instructionDetails").text(null);

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/recon/instruction/" + $("#searchField").val(),
        dataType: dataType,
        headers: headers,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(jqXHR.responseText);
            $("#error").css({
                "display": "block"
            });
            $("#error").text(textStatus + " " + jqXHR.responseText);
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data))
            $("#error").css({
                "display": "none"
            });

            let jsonPretty = JSON.stringify(data, null, '\t');
            $("#instructionDetails").text(jsonPretty);

            $("#ajax_loader").hide();
        }
    });
};

let receiptsSearchFunc = (isReceiptSearch) => {
    let headers = {
    "facilityNum": $("#facilityNumFormSelect").val(),
    "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
    "WMT-UserId": "report-user"
    };

    if (isReceiptSearch) {
        po = $("#searchField").val();
        poLine = $("#poLineSearchField").val();
    }

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/report/search/receipts",
        data: {
            "poNum": po,
            "poLineNum": poLine
        },
        headers: headers,
        dataType: dataType,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(jqXHR.responseText);
            $("#error").css({
                "display": "block"
            });
            $("#error").text(textStatus + " " + jqXHR.responseText);
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data));
            $("#error").css({
                "display": "none"
            });

            $("#receiptsDataSection").show();
            if (isReceiptSearch)
                $("#receiptsBackButton").hide();
            else
                $("#receiptsBackButton").show();
            $("#receiptsTitle").text("Receipts for PO " + po + ", line " + poLine);
            $("#receiptsTableBody").empty();
            let totalReceiptQty = 0;

            $.each(data, function (index, value) {
                let receiptsData = "<tr><td>" + value.createUserId + "</td><td>"
                    + value.deliveryNumber + "</td><td>"
                    + value.quantity + "</td><td>"
                    + value.quantityUom + "</td></tr>";
                totalReceiptQty = totalReceiptQty + value.quantity;
                $("#receiptsTableBody").append(receiptsData);
            });
            $("#receiptsTotalQty").text("Total receipt quantity is " + totalReceiptQty);
            $("#ajax_loader").hide();
        }
    })
};

let instructionDetails = (instruction) => {
    $("#instructionDetailsSection").show();
    $("#instructionsDataSection").hide();
    $("#instructionDetailsBackButton").show();
    let jsonPretty = JSON.stringify(instruction, null, '\t');
    $("#instructionDetails").text(jsonPretty);
};

let containerDetails = (container) => {
    $("#containerDetailsSection").show();
    $("#containersDataSection").hide();
    $("#containerDetailsBackButton").show();
    let jsonPretty = JSON.stringify(container, null, '\t');
    $("#containerDetails").text(jsonPretty);
};


$(document).ready(function () {
    $("#ajax_loader").hide();
    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#receiptsDataSection").hide();
    $("#containersDataSection").hide();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();
    $("#deliverySearchButton").hide();
    $("#poLineSearchFieldDiv").hide();
    $("#error").css({
        "display": "none"
    });
    $("#success").css({
        "display": "none"
    });

    document.getElementById("instructionsButton").disabled = true;
    document.getElementById("receiptsButton").disabled = true;
    document.getElementById("containersButton").disabled = true;
});

$("#searchForm").submit(function (event) {
    event.preventDefault();

    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#receiptsDataSection").hide();
    $("#containersDataSection").hide();
    $("#instructionsTableBody").empty();
    $("#receiptsTableBody").empty();
    $("#containersTableBody").empty();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();

    document.getElementById("instructionsButton").disabled = true;
    document.getElementById("receiptsButton").disabled = true;
    document.getElementById("containersButton").disabled = true;

    switch (selectedSearch) {
        case "delivery":
            deliverySearchFormFunc();
            break;
        case "container":
            containerSearchFunc();
            break;
        case "instruction":
            instructionSearchFunc();
            break;
        case "receipts":
            receiptsSearchFunc(true);
            break;
    }
});

$("#instructionsButton").click(function () {

    let headers = {
        "facilityNum": $("#facilityNumFormSelect").val(),
        "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
        "WMT-UserId": "report-user"
    };

    $("#deliveryDataSection").hide();

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/report/search/instructions",
        data: {
            "delivery": delivery,
            "poNum": po,
            "poLineNum": poLine,
        },
        headers: headers,
        dataType: dataType,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(jqXHR.responseText);
            $("#error").css({
                "display": "block"
            });
            $("#error").text(textStatus + " " + jqXHR.responseText);
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data));
            $("#error").css({
                "display": "none"
            });

            $("#instructionsDataSection").show();
            $("#instructionsTitle").text("Instructions for PO " + po + ", line " + poLine);
            $("#instructionsTableBody").empty();

            instructionData = data;

            $.each(data, function (index, value) {
                let checkboxId= HtmlEncode(value.id);
                let trackingId = value.container ? HtmlEncode(value.container.trackingId) : "";
                let instructionsData = "<tr><td><input id="
                    + checkboxId + " type='radio' name='optradioi' "
                    + "onclick='instructionCheckboxFunc(this.checked, this.id)'/> <td id='id_"
                    + checkboxId + "'>"
                    + HtmlEncode(value.id) + "</td><td>"
                    + HtmlEncode(value.messageId) + "</td><td>"
                    + HtmlEncode(value.activityName) + "</td><td>"
                    + HtmlEncode(value.projectedReceiveQty) + "</td><td id='rq_"
                    + checkboxId + "'>"
                    + HtmlEncode(value.receivedQuantity) + "</td><td>"
                    + HtmlEncode(value.projectedReceiveQtyUOM) + "</td><td>"
                    + trackingId + "</td>"
                    + "<td><button id='button_" + checkboxId
                    + "' class='btn btn-primary' onclick='instructionDetails(instructionData["
                    + index + "])'>View More</button></td></tr>";

                $("#instructionsTableBody").append(instructionsData);
            });
            $("#ajax_loader").hide();
        }
    })
});

$("#receiptsButton").click(function () {
    $("#deliveryDataSection").hide();
    receiptsSearchFunc(false);
});

$("#containersButton").click(function () {

    let headers = {
        "facilityNum": $("#facilityNumFormSelect").val(),
        "facilityCountryCode": $("#facilityCountryCodeFormSelect").val(),
        "WMT-UserId": "report-user"
    };

    $("#instructionsDataSection").hide();

    $("#ajax_loader").show();
    $.ajax({
        type: 'GET',
        url: "/recon/containers/instruction/" + instructionId,
        headers: headers,
        dataType: dataType,
        contentType: contentType,
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(textStatus, errorThrown);
            $("#error").css({
                "display": "block"
            });
            $("#error").html(textStatus + " " + errorThrown.toString());
            $("#ajax_loader").hide();
        },
        success: function (data, textStatus, jqXHR) {
            console.log(JSON.stringify(data));
            $("#error").css({
                "display": "none"
            });

            $("#containersDataSection").show();
            $("#containersTitle").text("Containers for Instruction ID " + instructionId);
            $("#containersTableBody").empty();

            containerData = data;

            $.each(data, function (index, value) {
                let checkboxId= HtmlEncode(value.id);
                let destination = value.destination ? HtmlEncode(value.destination['buNumber']) : "";
                let containersData = "<tr><td>" + HtmlEncode(value.trackingId) + "</td><td>"
                    + HtmlEncode(value.parentTrackingId) + "</td><td>"
                    + HtmlEncode(value.containerType) + "</td><td>"
                    + destination + "</td><td>"
                    + new Date(value.publishTs) + "</td>"
                    + "<td><button id='button_" + checkboxId
                    + "' class='btn btn-primary' onclick='containerDetails(containerData["
                    + index + "])'>View More</button></td></tr>";


                $("#containersTableBody").append(containersData);
            });
            $("#ajax_loader").hide();
        }
    });
});

$("#instructionsBackButton").click(function () {
    $("#instructionsDataSection").hide();
    $("#deliveryDataSection").show();
});

$("#receiptsBackButton").click(function () {
    $("#receiptsDataSection").hide();
    $("#deliveryDataSection").show();
});

$("#containersBackButton").click(function () {
    $("#containersDataSection").hide();
    $("#instructionsDataSection").show();
});

$("#instructionDetailsBackButton").click(function () {
    $("#instructionDetailsSection").hide();
    $("#instructionsDataSection").show();
    $("#instructionDetailsBackButton").hide();
});

$("#containerDetailsBackButton").click(function () {
    $("#containerDetailsSection").hide();
    $("#containersDataSection").show();
    $("#containerDetailsBackButton").hide();
});

$("#instructionSearchButton").click(function () {
    selectedSearch = 'instruction';
    $("#instructionSearchButton").hide();
    $("#containerSearchButton").show();
    $("#deliverySearchButton").show();
    $("#receiptsSearchButton").show();
    $("#searchFieldLabel").text("Enter Message Id");
    $("#searchField").attr("placeholder", "Search Instruction");
    $("#searchField").attr("type", "text");
    $("#poLineSearchFieldDiv").hide();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();
    $("#receiptsDataSection").hide();
    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#containersDataSection").hide();
    $("#searchField").val(null);
});

$("#containerSearchButton").click(function () {
    selectedSearch = 'container';
    $("#containerSearchButton").hide();
    $("#instructionSearchButton").show();
    $("#deliverySearchButton").show();
    $("#receiptsSearchButton").show();
    $("#searchFieldLabel").text("Enter Tracking Id");
    $("#searchField").attr("placeholder", "Search Container");
    $("#searchField").attr("type", "text");
    $("#poLineSearchFieldDiv").hide();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();
    $("#receiptsDataSection").hide();
    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#containersDataSection").hide();
    $("#searchField").val(null);
});

$("#deliverySearchButton").click(function () {
    selectedSearch = 'delivery';
    $("#deliverySearchButton").hide();
    $("#instructionSearchButton").show();
    $("#containerSearchButton").show();
    $("#receiptsSearchButton").show();
    $("#searchFieldLabel").text("Enter Delivery Number");
    $("#searchField").attr("placeholder", "Search Delivery");
    $("#searchField").attr("type", "number");
    $("#poLineSearchFieldDiv").hide();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();
    $("#receiptsDataSection").hide();
    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#containersDataSection").hide();
    $("#searchField").val(null);
});

$("#receiptsSearchButton").click(function () {
    selectedSearch = 'receipts';
    $("#receiptsSearchButton").hide();
    $("#instructionSearchButton").show();
    $("#containerSearchButton").show();
    $("#deliverySearchButton").show();
    $("#searchFieldLabel").text("Enter PO Number");
    $("#searchField").attr("placeholder", "Search PO");
    $("#searchField").attr("type", "text");
    $("#poLineSearchFieldDiv").show();
    $("#instructionDetailsSection").hide();
    $("#containerDetailsSection").hide();
    $("#receiptsDataSection").hide();
    $("#deliveryDataSection").hide();
    $("#instructionsDataSection").hide();
    $("#containersDataSection").hide();
    $("#searchField").val(null);
});


let poPoLineCheckboxFunc = (checked, id) => {
    if (checked) {
        $("input:checkbox").attr('disabled', 'disabled');
        document.getElementById(id).disabled = false;
    }
    document.getElementById("instructionsButton").disabled = !checked;
    document.getElementById("receiptsButton").disabled = !checked;
    po = $("#po_" + id).text();
    poLine = $("#po_line_" + id).text();
};

let instructionCheckboxFunc = (checked, id) => {
    if (checked) {
        $("input:checkbox").attr('disabled', 'disabled');
        document.getElementById(id).disabled = false;
    }
    if (document.getElementById("rq_"+id).textContent!=='0')
        document.getElementById("containersButton").disabled = !checked;
    else
        document.getElementById("containersButton").disabled = true;
    instructionId = id;
};
