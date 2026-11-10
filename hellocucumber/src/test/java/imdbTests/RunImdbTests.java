package imdbTests;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Runs the IMDB features in src/test/resources/imdbTests. Scenarios tagged @manual are skipped. */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("imdbTests")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "imdbTests")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/imdb.html")
public class RunImdbTests {
}
