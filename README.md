Maveniser
=========== 

Utility to generate Maven GAV(Group-Artiface-Version) for java libaries . Used when migrating to Maven/IVY dependency management from a ant/lib folder apporach.

Usage:   LibtoMaven (path to libs folder)  (mvn/ivy format output) -DisProxied=true

* Absoulute path to Lib folder with read access
* Dependency format 
	* mvn - generates  <dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version></dependency> 
	* ivy - generates <dependency org="{group}" name="{artifact}" rev="{version}" /> 
	
* isProxied Environment varible when using a proxy to connecto internet. Set  proxy-url,proxy-port etc in the appconfig.properties when using a proxy.


Current version: 1.0.0

