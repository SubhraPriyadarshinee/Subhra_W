package com.walmart.move.nim.receiving.core.transformer;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.*;

public class RxContainerTransformerTest {

    @InjectMocks private RxContainerTransformer rxContainerTransformer;

    @BeforeClass
    public void initMocks() {
        MockitoAnnotations.initMocks(this);

        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTransformList() {
        // Mock Container
        Container mockContainer = MockInstruction.getContainer();
        ContainerItem containerItem = new ContainerItem();
        containerItem.setPurchaseReferenceNumber("7");
        containerItem.setPurchaseReferenceLineNumber(5);
        containerItem.setInboundChannelMethod("CROSSU");
        containerItem.setOutboundChannelMethod("CROSSU");
        containerItem.setTotalPurchaseReferenceQty(100);
        containerItem.setPurchaseCompanyId(1);
        containerItem.setPoDeptNumber("0092");
        containerItem.setDeptNumber(1);
        containerItem.setItemNumber(10844432L);
        containerItem.setVendorGS128("");
        containerItem.setGtin("00049807100025");
        containerItem.setVnpkQty(1);
        containerItem.setWhpkQty(1);
        containerItem.setQuantity(1);
        containerItem.setQuantityUOM("EA");
        containerItem.setVendorPackCost(25.0);
        containerItem.setWhpkSell(25.0);
        containerItem.setBaseDivisionCode("VM");
        containerItem.setFinancialReportingGroupCode("US");
        containerItem.setRotateDate(new Date());
        containerItem.setDistributions(MockInstruction.getDistributions());
        containerItem.setLotNumber("ADC8908A");
        mockContainer.setContainerItems(Arrays.asList(containerItem));
        List<ContainerDTO> returnedObject = rxContainerTransformer.transformList(Arrays.asList(mockContainer));
        Assert.assertNotNull(returnedObject);
        Assert.assertEquals("a328990000000000000106509", returnedObject.get(0).getTrackingId());

    }

}