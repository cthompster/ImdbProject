This is the IMDB Coding Assignment

The objective is to implement BDD using cucumber.io and selenium frameworks to test IMDB web site behavior.
I have written the project in Java 1.8 and implemented maven to build the project and it's dependencies.
(Please see the pom.xml file to review build dependencies)


In this project I have followed basic BDD design using Feature files with Scenarios, and Scenario Outlines, along
with Selenium PageObjectModel structure, and both JUnit & TestNG.

For full documentation of Classes, Methods, DataProviders, Enums, Helper Classes, and resources please 
refer to the JavaDoc's directory here: Project/hellocucumber/javaDoc

How to Build the Project:
    hellocucumber is an IntelliJ project

Local Configuration:
    propertyFilePath (dataProviders/ConfigFileReader.java) - must be configured to point to a fqp on your system 
    configuration.properties - driverPath = driverPath= <your path to >/web_drivers/chromedriver
                example: driverPath=/Users/christiant/tools/web_drivers/chromedriver
                
    

Key Files:
    Feature Files: 
        1. top_rated_movies.feature  (_implemented_)
        2. title_display_page.feature
        
    Step Definitions:
        1. ImdbStepDefs.java
        2. TitleDisplaySTepDefs.java
    
    Test Runner:
        1. RunImdbTests.java (package imdbTests)
        2.  
  


Run The Tests:

    From the command line:  {mvn test}
    
    From the IDE: 