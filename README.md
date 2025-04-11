# Unified Warehouse Management System - Receiving [![Build Status](https://ci.logistics.walmart.com/buildStatus/icon?job=GLS-Nextgen/uwms-receiving)](https://ci.logistics.walmart.com/job/GLS-Nextgen/job/uwms-receiving/)

### Product Summary

The uwms-receiving product was developed as a part of the Atlas initiative.   
This is Wal-mart's cloud revolution and began with the conversion from the previous product [receive](https://gecgithub01.walmart.com/Logistics/receive) 
and [receive-container](https://gecgithub01.walmart.com/Logistics/receiving-container) to Java 8 and  Spring Boot.

### Sonar

- [![Coverage](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=coverage)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving) 
- [![Quality Gate Status](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=alert_status)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving)
- [![Bugs](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=bugs)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving) 
- [![Lines of Code](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=ncloc)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving) 
- [![Vulnerabilities](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=vulnerabilities)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving) 
- [![Maintainability Rating](https://sonar.looper.prod.walmartlabs.com/api/project_badges/measure?project=com.walmart.move.nim%3Auwms-receiving&metric=sqale_rating)](https://sonar.looper.prod.walmartlabs.com/dashboard?id=com.walmart.move.nim%3Auwms-receiving)

#### Frameworks and Build Requirements:  

-   Spring Boot: [https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)
-   Java 8: [https://docs.oracle.com/javase/8/docs/](https://docs.oracle.com/javase/8/docs/)
-   Maven: [https://maven.apache.org/](https://maven.apache.org/)

#### Testing Strategy

-   Unit Tests
    - TestNG: [https://testng.org/doc/index.html](https://testng.org/doc/index.html)
    - WireMock: [http://wiremock.org/](http://wiremock.org/)
-   Developer Integration Tests (DIT)
    - Cucumber: [https://docs.cucumber.io/](https://docs.cucumber.io/)
    - DIT Test Repository: [uwms-receiving.test](https://gecgithub01.walmart.com/Logistics/uwms-receiving.test)
    
#### CI &  CD Flow/Tools

-   Looper: [https://ci.logistics.walmart.com/job/GLS-Nextgen/job/uwms-receiving/](https://ci.logistics.walmart.com/job/GLS-Nextgen/job/uwms-receiving/)
    - Provisioned through [Strati](https://managed.services.prod.walmart.com/group/ms-sf-tomcat/system/receiving-api) tool
-   Concord: [https://concord.prod.walmart.com/#/org/Logistics/project/managedservlet-atlas-receiving/process](https://concord.prod.walmart.com/#/org/Logistics/project/managedservlet-atlas-receiving/process)
    - Provisioned through [Strati](https://managed.services.prod.walmart.com/group/ms-sf-tomcat/system/receiving-api) tool
-   OneOps: [https://oneops.prod.walmart.com/InFacilityProcessing/organization#summary](https://oneops.prod.walmart.com/InFacilityProcessing/organization#summary)
    - Provisioned through [Strati](https://managed.services.prod.walmart.com/group/ms-sf-tomcat/system/receiving-api) tool


Our logs are stored from our strati configured deployments on a Splunk server here:  [Splunk Logs](https://strati-logsearch01.prod.walmart.com/en-US/app/search/search?q=search%20index%3D%22apache_managed_tomcat_receiving-api%22%20sourcetype!%3Daccess_combined&sid=1548712656.38919_523A1191-EB70-445C-A3BB-772BC880CB3C&display.page.search.mode=smart&dispatch.sample_ratio=1&earliest=-15m&latest=now)


### Contribution guidelines

Except Log and health check controllers all controller must be tenant aware and should handle
Tenant context either Via aspect or MVC Filter ( discovery by component scan)

Please make sure you follow our pull request template and update any changes needed in configurations to

Cloud Configuration Management(CCM): [https://admin.ccm.stg.walmart.com/](https://admin.ccm.stg.walmart.com/)

Please run: ``` mvn com.coveo:fmt-maven-plugin:format ``` in order to auto format your code according to our standardized style.

### Lombok setup

If you are seeing compiler errors with getters and setters within your project please see these links to set up your IDE to handle lombok annotations.

[IntelliJ lombok setup](https://stackoverflow.com/a/42809311)

[Eclipse lombok setup](https://howtodoinjava.com/automation/lombok-eclipse-installation-examples/)

# Configuration
1. ### Add these properties into a property file(env.properties)
```
runtime.context.system.property.override.enabled=true
runtime.context.appVersion=0.0.1-SNAPSHOT
runtime.context.appName=receiving-api
runtime.context.environment=dev-local
runtime.context.environmentType=dev
```
2. ### Disabled CCM server access until project onboarding is complete
```
scm.server.access.enabled=true
scm.snapshot.enabled=true
```
3. see the configuration `application.properties`
</br> by default no changes required
4. Certs/secrets
- From devs get files 1.receiving-secrets.properties, 2.gls-atlas-receiving-api-kafka.nonprod.walmart.com.jks and 3.receiving-sumo.properties
- Place all the three files in /etc/secrets/ (secrets folder if not there, create)
- Ensure write access to both the files

6. ### Add these VM arguments
```
-Dspring.config.additional-location=/etc/secrets/receiving-secrets.properties,/etc/secrets/receiving-sumo.properties
-Dplatform=local 
-Dspring.config.use-legacy-processing=true  
-Dspring.main.allow-circular-references=true
-Denabled.http.basicconnectionmanager=true
 ```
# run the app
```sh
$ mvn spring-boot:run 
```

-Check if app deployed successfully by ensuring similar below logs (dispatcherServlet': initialization completed) prited
``` 
WMPLTFMLOG523516	1645720973728	2022-02-24 11:42:53.728	192.168.0.6	-	-	-	-	dev	receiving-api	unknown	dev-local	2.0.0-SNAPSHOT	ffffffff8433ca1a-153-17f2c9d31a0000	INFO	INFO	-	-	-	-	applog.cls=org.springframework.web.servlet.FrameworkServlet,applog.mthd=initServletBean,applog.line=509,applog.msg=FrameworkServlet 'dispatcherServlet': initialization completed in 65 ms	[]	[]	[-]	[]	[RMI TCP Connection(22)-192.168.0.6]
```
### FAQs:
#### Exception jks file not found
```Caused by: java.io.FileNotFoundException: /etc/secrets/gls-atlas-receiving-api-kafka.nonprod.walmart.com.jks (No such file or directory)```
#### Resolution: 
- ensure the file gls-atlas-receiving-api-kafka.nonprod.walmart.com.jks in above path 
- and having read/write access 
- ensure VM has `-Dplatform=local`
- 

#### References
https://collaboration.wal-mart.com/display/NGRCV/Kafka+secure+trustStore+file+manage

