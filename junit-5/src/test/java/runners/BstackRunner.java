package runners;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import utils.SetupLocalTesting;

import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

public class BstackRunner implements TestTemplateInvocationContextProvider {
    public WebDriver driver;
    public DesiredCapabilities capabilities;
    public String username, accesskey, server;
    private JSONObject mainConfig;
    private JSONObject browserConfig;
    private JSONObject profileConfig;
    private JSONObject testConfig;
    private JSONObject platformConfig;
    private JSONObject commonCapsConfig;
    private HashMap<String, String> allCapsMap;
    private HashMap<String, String> commonCapsMap;

    public BstackRunner() {
        this.username = setupCredsAndServer().get("username");
        this.accesskey = setupCredsAndServer().get("accesskey");
        this.server = setupCredsAndServer().get("server");
    }

    public HashMap<String, String> setupCredsAndServer() {
        try {
            JSONParser parse = new JSONParser();
            mainConfig = (JSONObject) parse.parse(new FileReader("src/test/resources/caps.json"));
            server = (String) mainConfig.get("server");
            username = System.getenv("BROWSERSTACK_USERNAME");
            if (username == null) {
                username = (String) mainConfig.get("user");
            }
            accesskey = System.getenv("BROWSERSTACK_ACCESS_KEY");
            if (accesskey == null) {
                accesskey = (String) mainConfig.get("key");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        HashMap<String, String> creds = new HashMap();
        creds.put("username", username);
        creds.put("accesskey", accesskey);
        creds.put("server", server);
        return creds;
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext extensionContext) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext extensionContext) {
        List<TestTemplateInvocationContext> desiredCapsInvocationContexts = new ArrayList<>();
        //picks the test profile based on the maven command executed - single, local, parallel
        String profile = System.getProperty("config");

        try {
            testConfig = (JSONObject) mainConfig.get("tests");
            profileConfig = (JSONObject) testConfig.get(profile);
            platformConfig = (JSONObject) profileConfig.get("platform");
            commonCapsConfig = (JSONObject) profileConfig.get("common_caps");
            commonCapsMap = (HashMap<String, String>) commonCapsConfig;
            Iterator platformIterator = platformConfig.keySet().iterator();

            while (platformIterator.hasNext()) {
                capabilities = new DesiredCapabilities();
                Iterator commonCapsIterator = commonCapsMap.entrySet().iterator();
                while (commonCapsIterator.hasNext()) {
                    Map.Entry capsName = (Map.Entry) commonCapsIterator.next();
                    capabilities.setCapability((String) capsName.getKey(), capsName.getValue());
                }
                final String platformName = (String) platformIterator.next();
                browserConfig = (JSONObject) platformConfig.get(platformName);
                allCapsMap = (HashMap<String, String>) browserConfig;
                Iterator finalCapsIterator = allCapsMap.entrySet().iterator();
                while (finalCapsIterator.hasNext()) {
                    Map.Entry pair = (Map.Entry) finalCapsIterator.next();
                    capabilities.setCapability((String) pair.getKey(), pair.getValue());
                }
                //Initializing local testing connection
                if (capabilities.getCapability("browserstack.local") != null && capabilities.getCapability("browserstack.local").toString().equals("true")) {
                    HashMap<String, String> localOptions = new HashMap<>();
                    localOptions.put("key", accesskey);
                    //Add more local options here, e.g. forceLocal, localIdentifier, etc.
                    SetupLocalTesting.createInstance(localOptions);
                }
                desiredCapsInvocationContexts.add(invocationContext(capabilities));

            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return desiredCapsInvocationContexts.stream();
    }

    private TestTemplateInvocationContext invocationContext(DesiredCapabilities caps) {
        return new TestTemplateInvocationContext() {

            @Override
            public List<Extension> getAdditionalExtensions() {

                return Collections.singletonList(new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(ParameterContext parameterContext,
                                                     ExtensionContext extensionContext) {
                        return parameterContext.getParameter().getType().equals(WebDriver.class);
                    }

                    @Override
                    public Object resolveParameter(ParameterContext parameterContext,
                                                   ExtensionContext extensionContext) {
                        try {
                            driver = new RemoteWebDriver(new URL("https://" + username + ":" + accesskey + "@" + server + "/wd/hub"), caps);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                        return driver;
                    }
                });
            }
        };
    }
}
