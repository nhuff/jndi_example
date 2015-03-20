import javax.naming.*;
import javax.naming.directory.*;
import java.util.Hashtable;
import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;

public class JndiExample {
  private static Properties conf = loadConfig();

  public static void main(String[] args) {
    Hashtable<String,Object> env = createEnv();
    DirContext ctx;

    // Get the search filter template from configuration
    String filter_template = conf.getProperty("search_filter");

    // Read username and password from user
    String username = getUsername();
    String pass = getPassword();

    // Use template and username to construct an LDAP search string
    String filter = String.format(filter_template,username);

    try {
      // Bind the first time to lookup the users DN
      ctx = new InitialDirContext(env);
    } catch(NamingException ne) {
      error(ne.toString());
      return;
    }

    // Get the dn of the user if it exists
    String dn = getDN(ctx,filter);

    try {
      // Close our initial connection to the ldap server
      ctx.close();
      // Change the security credentials to the dn we looked up
      // and the password the user entered
      env.put(Context.SECURITY_PRINCIPAL,dn);
      env.put(Context.SECURITY_CREDENTIALS, pass);
      // Try to bind as that user
      ctx = new InitialDirContext(env);
    } catch(NamingException ne) {
      // Bind failed
      error(ne.toString());
    }

    // Successfully authenticated user
    System.out.println("Success");
  }

  // Read the username
  private static String getUsername() {
    return System.console().readLine("username: ");
  }

  // Read the password
  private static String getPassword() {
    char[] pass =  System.console().readPassword("password: ");
    return new String(pass);
  }

  // Using the given filter find the user object and return
  // the DN of the object
  private static String getDN(DirContext ctx,String filter) {
    NamingEnumeration<SearchResult> answers;
    String ret = null;
    SearchControls ctls = createSearchControls();
    try {
      answers = ctx.search(conf.getProperty("search_base"),filter,ctls);
      SearchResult sr = answers.next();
      ret = sr.getNameInNamespace();
    } catch(NamingException ne) {
      error(ne.toString());
    }
    return ret;
  }

  private static void error(String error) {
    System.err.println(error);
    System.exit(1);
  }

  // Create the initial enviroment for the DirContext
  private static Hashtable<String,Object> createEnv() {
    Hashtable<String,Object> env = new Hashtable<String,Object>();
    // Doing ldap
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    // Set timeouts to something sane so we don't hang forever
    env.put("com.sun.jndi.ldap.connect.timeout", "5000");
    env.put("com.sun.jndi.ldap.read.timeout", "1000");
    // Get ldap url from config
    env.put(Context.PROVIDER_URL, conf.getProperty("ldap_url"));
    // Do simple auth
    env.put(Context.SECURITY_AUTHENTICATION,"simple");
    // If we configured an initial user to bind as, use username
    // and password from config for bind.
    //
    // If there isn't one configured it will do an anonymous bind
    if(conf.getProperty("bind_dn") != null) {
      env.put(Context.SECURITY_PRINCIPAL,conf.getProperty("bind_dn"));
      env.put(Context.SECURITY_CREDENTIALS, conf.getProperty("bind_password"));
    }
    return env;
  }

  // Setup some basic controls for the search
  private static SearchControls createSearchControls() {
    // I only need the dn from the search
    String[] attrIDs = {"dn"};
    SearchControls ctls = new SearchControls();
    ctls.setReturningAttributes(attrIDs);

    // Set the search scope. Probably want subtree here so the search
    // will go down the directory tree. Onelevel might make sense in certain
    // circumstances as well
    String scope = conf.getProperty("search_scope");
    if(scope.equals("subtree")) {
      ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    } else if (scope.equals("onelevel")) {
      ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    } else if (scope == null) {
      // Don't set anything
    } else {
      error("Unrecognized search scope: "+scope);
    }

    return ctls;
  }

  // load our initial config
  private static Properties loadConfig() {
    Properties prop = new Properties();
    InputStream input = null;
    try {
      input = JndiExample.class.getClassLoader().getResourceAsStream("config.properties");
      prop.load(input);
    } catch (IOException ex) {
      ex.printStackTrace();
      error("Couldn't load properties file");
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return prop;
  }
}
