## Hygieia collector for collecting Library Policy results from Whitesource

Hygieia WhiteSource Collector

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.capitalone.dashboard/whitesource-collector.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.capitalone.dashboard%22%20AND%20a:%22whitesource-collector%22)
[![Build Status](https://travis-ci.com/Hygieia/hygieia-whitesource-collector.svg?branch=main)](https://travis-ci.com/Hygieia/hygieia-whitesource-collector)
[![Gitter Chat](https://badges.gitter.im/Join%20Chat.svg)](https://www.apache.org/licenses/LICENSE-2.0)
<br>
<br>

Configure the WhiteSource Collector to display and monitor information (related to library policies) on the Hygieia Dashboard, from WhiteSource. Hygieia uses Spring Boot to package the collector as an executable JAR file with dependencies.
Please refer https://whitesource.atlassian.net/wiki/spaces/WD/pages/814612683/HTTP+API+v1.3 for api documentation.

# Table of Contents
* [Setup Instructions](#setup-instructions)
* [Sample Application Properties](#sample-application-properties)
* [Run collector with Docker](#run-collector-with-docker)

### Setup Instructions

To configure your collector, execute the following steps: 

*	**Step 1 - Artifact Preparation:**

	Please review the two options in Step 1 to find the best fit for you. 

	***Option 1 - Download the artifact:***

	You can download the SNAPSHOTs from the SNAPSHOT directory [here](https://oss.sonatype.org/content/repositories/snapshots/com/capitalone/dashboard/whitesource-collector/) or from the maven central repository [here](https://search.maven.org/artifact/com.capitalone.dashboard/whitesource-collector).  

	***Option 2 - Build locally:***

	To configure your collector, git clone the [whitesource collector repo](https://github.com/Hygieia/hygieia-whitesource-collector).  Then, execute the following steps:

	To package the whitesource collector source code into an executable JAR file, run the maven build from the `\hygieia-whitesource-collector` directory of your source code installation:

	```bash
	mvn install
	```

	The output file `[collector name].jar` is generated in the `hygieia-whitesource-collector\target` folder.

	Once you have chosen an option in Step 1, please proceed: 

*   **Step 2: Set Parameters in Application Properties File**

Set the configurable parameters in the `application.properties` file to connect to the Dashboard MongoDB database instance, including properties required by the WhiteSource Collector.

To configure parameters for the WhiteSource Collector, refer to the sample [application.properties](#sample-application-properties) section.

For information about sourcing the application properties file, refer to the [Spring Boot Documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files).

*   **Step 3: Deploy the Executable File**

To deploy the `[collector name].jar` file, change directory to `hygieia-whitesource-collectorr\target`, and then execute the following from the command prompt:

```bash
java -jar [collector name].jar --spring.config.name=whitesource --spring.config.location=[path to application.properties file]
```

### Sample Application Properties

The sample `application.properties` lists parameters with sample values to configure the WhiteSource Collector. Set the parameters based on your environment setup.

```properties
		# Database Name
		dbname=dashboarddb

		# Database HostName - default is localhost
		dbhost=10.0.1.1

		# Database Port - default is 27017
		dbport=27017

		# MongoDB replicaset
		dbreplicaset=[false if you are not using MongoDB replicaset]
		dbhostport=[host1:port1,host2:port2,host3:port3]

		# Database Username - default is blank
		dbusername=dashboarduser

		# Database Password - default is blank
		dbpassword=dbpassword

		# Collector schedule (required)
		whitesource.cron=0 0/5 * * * *

		# WhiteSource server(s) (required) - Can provide multiple
		whitesource.servers[0]=http://whitesource.company.com

		# WhiteSource userKey - provided for read access
		whitesource.userKey=
		
	    	# WhiteSource orgToken - Organization token to identify an organization in whitesource - Can provide multiple
		whitesource.orgToken[0]=

	    	# WhiteSource sleeTime - can manual inject thread sleep-time between transations to whitesource apis
	    	whitesource.sleepTime=150              

	    	# WhiteSource requestRateLimit - threshold for rate-limit 
	    	whitesource.requestRateLimit=3            

	    	# WhiteSource requestRateLimitTimeWindow
	    	whitesource.requestRateLimitTimeWindow=1000

	    	# WhiteSource errorResetWindow
	    	whitesource.errorResetWindow = 36000

	    	# WhiteSource highLicensePolicyTypes - transalation of license violations to HIGH severity (Enterprise specific) - can be multiple
	    	whitesource.criticalLicensePolicyTypes[0].policyName=
		whitesource.criticalLicensePolicyTypes[0].descriptions[0]=

		whitesource.highLicensePolicyTypes[0].policyName=
		whitesource.highLicensePolicyTypes[0].descriptions[0]=
		

		whitesource.mediumLicensePolicyTypes[0].policyName=
		whitesource.mediumLicensePolicyTypes[0].descriptions[0]=

		whitesource.lowLicensePolicyTypes[0].policyName=
		whitesource.lowLicensePolicyTypes[0].descriptions[0]=
    
```

## Run collector with Docker

You can install Hygieia by using a docker image from docker hub. This section gives detailed instructions on how to download and run with Docker. 

*	**Step 1: Download**

	Navigate to the docker hub location of your collector [here](https://hub.docker.com/u/hygieiadoc) and download the latest image (most recent version is preferred).  Tags can also be used, if needed.

*	**Step 2: Run with Docker**

	```Docker run -e SKIP_PROPERTIES_BUILDER=true -v properties_location:/hygieia/config image_name```
	
	- <code>-e SKIP_PROPERTIES_BUILDER=true</code>  <br />
	indicates whether you want to supply a properties file for the java application. If false/omitted, the script will build a properties file with default values
	- <code>-v properties_location:/hygieia/config</code> <br />
	if you want to use your own properties file that located outside of docker container, supply the path here. 
		- Example: <code>-v /Home/User/Document/application.properties:/hygieia/config</code>
