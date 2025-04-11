#!/bin/ksh

userId=jethoma
corr=123
deliveryNumber=121212121
facilityNum=32612
facilityCountryCode=US
basePath="http://localhost:8080/test/putaway/rcv-00?deliveryNumber=${deliveryNumber}&"

function postItem
{
  itemNbr=${1}

  print "Item: ${itemNbr}"

  ## add -i if you want to see http status
  curl -w ": %{response_code} \n" \
   -H "Content-Type: application/json" \
   -H "WMT-Userid: ${userId}" \
   -H "WMT-CorrelationId: ${corr}" \
   -H "facilityNum: ${facilityNum}" \
   -H "facilityCountryCode: ${facilityCountryCode}" \
   -X POST ${basePath}itemNumber=${itemNbr}
}

#----------
# OPM items
#----------
# postItem 9174699,  sent: 023246087875653340, failed ... doesn't like 9 digit vendor
# postItem 9174699,  sent: 023251733296680269, failed ... same error, cacheing problem
# postItem 9174699,  sent: 023255109811460193, failed ... same error, had to restart eclipse to clear cache
# postItem 9174699,  sent: 023207376617854135
#postItem 9174699
#postItem 573487883
#postItem 573105334

#----------
# CPS items
#----------
#postItem 9055195
#postItem 9072601
#postItem 9416152
#postItem 9417850
#postItem 9421886
#postItem 9440809
#postItem 9862196
#postItem 550011859
#postItem 550296074
#postItem 554930276
#postItem 555238935
#postItem 555803971
#postItem 555998246
#postItem 556552694
#postItem 567917234
#postItem 568140989
#postItem 568234543
#postItem 569572132
#postItem 570563622
#postItem 572042314
#postItem 574915957

#------------
# Manual item
#------------
postItem 553237983
