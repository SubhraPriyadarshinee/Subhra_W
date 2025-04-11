/** */
package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.Application;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** @author m0g028p */
@ActiveProfiles("test")
@SpringBootTest(
    classes = {Application.class},
    properties = {
      "secrets.key=AtlasReceivingkey",
      "enable.receiving.secure.kafka=false",
      "is.hawkeye.on.secure.kafka=false",
      "is.gdm.on.secure.kafka=false",
      "enable.acc.hawkeye.queue=false",
      "spring.main.allow-bean-definition-overriding=true"
    })
@AutoConfigureMockMvc
public class TenantSpecificConfigReaderTestBase extends ReceivingControllerTestBase {

  @Autowired @MockBean protected TenantSpecificConfigReader tenantSpecificConfigReader;
}
