import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.*;
import java.io.*;
import java.util.HashMap;


/**
 * A class to demonstrate a simple client-server connection using sockets.
 *
 */
public class SockServer {
  static Socket sock;
  static DataOutputStream os;
  static ObjectInputStream in;

  static int port = 8888;

  public static void main (String args[]) {

    if (args.length != 1) {
      System.out.println("Expected arguments: <port(int)>");
      System.exit(1);
    }

    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException nfe) {
      System.out.println("[Port|sleepDelay] must be an integer");
      System.exit(2);
    }

    try {
      //open socket
      ServerSocket serv = new ServerSocket(port);
      System.out.println("Server ready for connections");

      /**
       * Simple loop accepting one client and calling handling one request.
       *
       */


      while (true){
        System.out.println("Server waiting for a connection");
        sock = serv.accept(); // blocking wait
        System.out.println("Client connected");

        // setup the object reading channel
        in = new ObjectInputStream(sock.getInputStream());

        // get output channel
        OutputStream out = sock.getOutputStream();

        // create an object output writer (Java only)
        os = new DataOutputStream(out);

        boolean connected = true;
        while (connected) {
          String s = "";
          try {
            s = (String) in.readObject(); // attempt to read string in from client
          } catch (Exception e) { // catch rough disconnect
            System.out.println("Client disconnect");
            connected = false;
            continue;
          }

          JSONObject res = isValid(s);

          if (res.has("ok")) {
            writeOut(res);
            continue;
          }

          JSONObject req = new JSONObject(s);

          res = testField(req, "type");
          if (!res.getBoolean("ok")) { // no "type" header provided
            res = noType(req);
            writeOut(res);
            continue;
          }
          // check which request it is (could also be a switch statement)
          if (req.getString("type").equals("echo")) {
            res = echo(req);
          } else if (req.getString("type").equals("add")) {
            res = add(req);
          } else if (req.getString("type").equals("addmany")) {
            res = addmany(req);
          } else if (req.getString("type").equals("charcount")) {
            res = charcount(req);  // Added line
          } else if (req.getString("type").equals("inventory")) {
            res = inventory(req);  // Added this line
          } else {
            res = wrongType(req);
          }
          writeOut(res);
        }
        // if we are here - client has disconnected so close connection to socket
        overandout();
      }
    } catch(Exception e) {
      e.printStackTrace();
      overandout(); // close connection to socket upon error
    }
  }


  /**
   * Checks if a specific field exists
   *
   */
  static JSONObject testField(JSONObject req, String key){
    JSONObject res = new JSONObject();

    // field does not exist
    if (!req.has(key)){
      res.put("ok", false);
      res.put("message", "Field " + key + " does not exist in request");
      return res;
    }
    return res.put("ok", true);
  }

  // handles the simple echo request
  static JSONObject echo(JSONObject req){
    System.out.println("Echo request: " + req.toString());
    JSONObject res = testField(req, "data");
    if (res.getBoolean("ok")) {
      if (!req.get("data").getClass().getName().equals("java.lang.String")){
        res.put("ok", false);
        res.put("message", "Field data needs to be of type: String");
        return res;
      }

      res.put("type", "echo");
      res.put("echo", "Here is your echo: " + req.getString("data"));
    }
    return res;
  }

  // handles the simple add request with two numbers
  static JSONObject add(JSONObject req){
    System.out.println("Add request: " + req.toString());
    JSONObject res1 = testField(req, "num1");
    if (!res1.getBoolean("ok")) {
      return res1;
    }

    JSONObject res2 = testField(req, "num2");
    if (!res2.getBoolean("ok")) {
      return res2;
    }

    JSONObject res = new JSONObject();
    res.put("ok", true);
    res.put("type", "add");
    try {
      res.put("result", req.getInt("num1") + req.getInt("num2"));
    } catch (org.json.JSONException e){
      res.put("ok", false);
      res.put("message", "Field num1/num2 needs to be of type: int");
    }
    return res;
  }

  // implement me in assignment 3

  static HashMap<String, Integer> inventory = new HashMap<>();

  static JSONObject inventory(JSONObject req) {
    JSONObject res = new JSONObject();
    try {
      String task = req.getString("task");
      switch (task) {
        case "add":
          String productName = req.getString("productName");
          int quantityToAdd = req.getInt("quantity");
          inventory.put(productName, inventory.getOrDefault(productName, 0) + quantityToAdd);
          res.put("ok", true);
          res.put("type", "inventory");
          res.put("message", "Product added successfully.");
          break;
        case "view":
          JSONArray productList = new JSONArray();
          inventory.forEach((product, quantity) -> {
            JSONObject item = new JSONObject();
            item.put("product", product);
            item.put("quantity", quantity);
            productList.put(item);
          });
          res.put("ok", true);
          res.put("type", "inventory");
          res.put("inventory", productList);
          break;
        case "buy":
          productName = req.getString("productName");
          int quantityToBuy = req.getInt("quantity");
          if (!inventory.containsKey(productName)) {
            res.put("ok", false);
            res.put("message", "Product " + productName + " not in inventory");
          } else if (inventory.get(productName) < quantityToBuy) {
            res.put("ok", false);
            res.put("message", "Product " + productName + " not available in quantity " + quantityToBuy);
          } else {
            inventory.put(productName, inventory.get(productName) - quantityToBuy);
            res.put("ok", true);
            res.put("type", "inventory");
            res.put("message", "Product bought successfully.");
          }
          break;
        default:
          res.put("ok", false);
          res.put("message", "Invalid task type.");
          break;
      }
    } catch (JSONException e) {
      res.put("ok", false);
      res.put("message", "Error processing inventory request: " + e.getMessage());
    }
    return res;
  }

  static JSONObject charcount(JSONObject req) {
    JSONObject res = new JSONObject();
    try {
      String text = req.getString("count");  // Extract text to be searched.
      boolean findChar = req.getBoolean("findchar");  // Determine whether to search for a specific character.
      if (findChar) {
        String find = req.getString("find");
        if (find.length() != 1) {
          throw new JSONException("Find must be a single character.");  // Ensure only one character is searched.
        }
        char target = find.charAt(0);  // Get the character to find.
        long charcount = text.chars().filter(c -> c == target).count();  // Count occurrences of the character.
        res.put("ok", true);
        res.put("type", "charcount");
        res.put("result", charcount);  // Return count of the specific character.
      } else {
        res.put("ok", true);
        res.put("type", "charcount");
        res.put("result", text.length());  // Return total length of the string.
      }
    } catch (JSONException e) {
      res.put("ok", false);
      res.put("message", "Error processing charCount request: " + e.getMessage());  // Handle JSON parsing errors.
    }
    return res;
  }

  static JSONObject addmany(JSONObject req){
    System.out.println("Add many request: " + req.toString());
    JSONObject res = testField(req, "nums");
    if (!res.getBoolean("ok")) {
      return res;
    }

    int result = 0;
    JSONArray array = req.getJSONArray("nums");
    for (int i = 0; i < array.length(); i ++){
      try{
        result += array.getInt(i);
      } catch (org.json.JSONException e){
        res.put("ok", false);
        res.put("message", "Values in array need to be ints");
        return res;
      }
    }

    res.put("ok", true);
    res.put("type", "addmany");
    res.put("result", result);
    return res;
  }

  // creates the error message for wrong type
  static JSONObject wrongType(JSONObject req){
    System.out.println("Wrong type request: " + req.toString());
    JSONObject res = new JSONObject();
    res.put("ok", false);
    res.put("message", "Type " + req.getString("type") + " is not supported.");
    return res;
  }

  // creates the error message for no given type
  static JSONObject noType(JSONObject req){
    System.out.println("No type request: " + req.toString());
    JSONObject res = new JSONObject();
    res.put("ok", false);
    res.put("message", "No request type was given.");
    return res;
  }

  // From: https://www.baeldung.com/java-validate-json-string
  public static JSONObject isValid(String json) {
    try {
      new JSONObject(json);
    } catch (JSONException e) {
      try {
        new JSONArray(json);
      } catch (JSONException ne) {
        JSONObject res = new JSONObject();
        res.put("ok", false);
        res.put("message", "req not JSON");
        return res;
      }
    }
    return new JSONObject();
  }

  // sends the response and closes the connection between client and server.
  static void overandout() {
    try {
      os.close();
      in.close();
      sock.close();
    } catch(Exception e) {e.printStackTrace();}

  }

  // sends the response and closes the connection between client and server.
  static void writeOut(JSONObject res) {
    try {
      os.writeUTF(res.toString());
      // make sure it wrote and doesn't get cached in a buffer
      os.flush();

    } catch(Exception e) {e.printStackTrace();}

  }
}