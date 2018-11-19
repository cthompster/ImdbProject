**This is the IMDB Coding Assignment**

The objective is to implement BDD using cucumber.io and selenium frameworks to test IMDB web site behavior.
I have written the project in Java 1.8 and implemented maven to build the project and it's dependencies.
(Please see the pom.xml file to review build dependencies)


In this project I have followed basic BDD design using Feature files with Scenarios, and Scenario Outlines, along
with Selenium PageObjectModel structure, and both JUnit & TestNG.

For full documentation of Classes, Methods, DataProviders, Enums, Helper Classes, and resources please 
refer to the JavaDoc's directory here: Project/hellocucumber/javaDoc

**How to Build the Project:**

    hellocucumber (ImdbProject) is an IntelliJ project built with Maven

**Local Configuration:**
    propertyFilePath (dataProviders/ConfigFileReader.java) - must be configured to point to a fqp on your system 
    configuration.properties - driverPath = driverPath= <your path to >/web_drivers/chromedriver
                example: driverPath=/Users/christiant/tools/web_drivers/chromedriver
                
    

**Key Files:**
    
    Feature Files: 
        1. top_rated_movies.feature  (_implemented_)
        2. title_display_page.feature
        
    Step Definitions:
        1. ImdbStepDefs.java
        2. TitleDisplaySTepDefs.java
    
    Test Runner:
        1. RunImdbTests.java (package imdbTests)

  


**Run The Tests:**

    From command line:  
        1. Open a terminal or CMD
        2. Navigate to project direcotry
           
           Example: cd /IdeaProjects/ImdbProject/hellocucumber
        
        3. run: mvn test
    
    From the IDE:  
        1. Open your IDE (IntelliJ)
        2. Import the ImdbProject
        3. Navigate to the Feature files --> /hellocucumber/src/test/resources/imdbTests
        4. Open "top_rated_movies.feature" file
        5. Rigth-Click on the line with "Feature: Top Rated Movies"
        6. In the pop-up menu select: "Run 'Feature: Top Rated Movies'