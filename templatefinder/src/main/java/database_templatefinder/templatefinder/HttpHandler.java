package database_templatefinder.templatefinder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
        String msg = "<html><body>";
        Map<String, String> parms = session.getParms();
        if (parms.get("querystring") == null) {
        	msg += "<h1>Template checker</h1>\n";
            msg += "<form action='?' method='get'>\n  <p>Please input string to check: <input type='text' name='querystring'>" + 
            		" <input type='checkbox' name='userfriendly' value='true'> User friendly mode (uncheck for raw JSON)<br></p>\n" + "</form>\n";
        } else if("true".equals(parms.get("userfriendly"))) {
        	// Check template
        	final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
                App.processInput(parms.get("querystring"), ps);
            } catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
            String data = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            
            for(String line : data.split("\n")) {
                msg += "<p>" + line + "</p>";
            }
            try {
            	msg += "<p>" + App.processInput(parms.get("querystring"), null).getJsonOutputObject().toString() + "</p>";
            }
            catch(Exception ex) {
            	ex.printStackTrace();
            }
        }
        else {
        	JsonObject js = App.processInput(parms.get("querystring"), null).getJsonOutputObject();
        	msg += "<p>" + "result: " + js.toString() + "</p>";
        	
        }
        return newFixedLengthResponse(msg + "</body></html>");
    }
}