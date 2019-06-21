package database_templatefinder.templatefinder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import fi.iki.elonen.NanoHTTPD;

/**
 * Modified from GitHub NanoHTTPD example.
 */
public class HttpHandler extends NanoHTTPD {

    public HttpHandler() throws IOException {
        super(57421);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("The NanoHTTPD HTTP server is now running on http://localhost:57421/");
    }

    @Override
    public Response serve(IHTTPSession session) {
    	try {
            Map<String, String> parms = session.getParms();
            if (parms.get("querystring") == null) {
                String msg = "<html><body>";
            	msg += "<h1>Template checker</h1>\n";
                msg += "<form action='?' method='get'>\n  <p>Please input string to check: <input type='text' name='querystring'>" + 
                		" <input type='checkbox' name='userfriendly' value='true'> User friendly mode (uses outdated processing method)<br></p>\n" + "</form>\n";
                return newFixedLengthResponse(msg + "</body></html>");
            } else if("true".equals(parms.get("userfriendly"))) {
                String msg = "<html><body>";
            	// Check template
            	final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
                	InputProcessing.legacyProcessInput(parms.get("querystring"), ps);
                } catch (UnsupportedEncodingException e) {
    				e.printStackTrace();
    			}
                String data = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                
                for(String line : data.split("\n")) {
                    msg += "<p>" + line + "</p>";
                }
                try {
                	msg += "<p>" + InputProcessing.legacyProcessInput(parms.get("querystring"), null).getJsonOutputObject().toString() + "</p>";
                }
                catch(Exception ex) {
                	ex.printStackTrace();
                }
                return newFixedLengthResponse(msg + "</body></html>");
            }
            else {
            	JsonArray js = InputProcessing.toJson(InputProcessing.process(parms.get("querystring")));
            	return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", js.toString());
            }
    	}
    	catch(Exception ex) {
    		ex.printStackTrace();
    		return null;
    	}
    }
}