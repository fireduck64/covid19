package duck.covid19;

import duckutil.PeriodicThread;
import duckutil.ConfigFile;
import duckutil.Config;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.File;
import java.util.LinkedList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.StringTokenizer;
import duckutil.TaskMaster;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.logging.Logger;
import java.net.URI;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class CovidQuery
{
  public static void main(String args[]) throws Exception
  {
    new CovidQuery(new ConfigFile(args[0]));
  }

  private static final Logger logger = Logger.getLogger("covidquery");

  protected volatile TreeMap<String,Integer> stateToFips = new TreeMap<>();
  protected volatile TreeMap<String,TreeMap<String, Integer> > countyToFips = new TreeMap<>();
  protected volatile TreeSet<String> dates = new TreeSet<>();
  protected volatile TreeMap<Integer, String> fips_lookup=new TreeMap<>();
  protected volatile ImmutableMap<Integer, Long> pop_by_fips = null;
  protected volatile ImmutableMap<Integer, LocationData> location_data_map = null;
  protected volatile ImmutableMap<Integer, LocationData> location_data_avg_map = null;
  public static final Integer US_FIPS = 0;

  protected final Config config;
  public CovidQuery(Config conf)
    throws Exception
  {
    this.config = conf;
    new UpdateThread().start();
    Thread.sleep(1000);

    new WebServer();

  }

  protected void addDate(String date)
  {
    synchronized(dates)
    {
      if (dates.contains(date)) return;
      dates.add(date);
    }
  }


  protected void addState(String state, int fips)
  {
    synchronized(stateToFips)
    {
      if (stateToFips.containsKey(state)) return;

      stateToFips.put(state, fips);
      synchronized(fips_lookup)
      {
        fips_lookup.put(fips, state);
      }

    }
    
  }
  protected void addCounty(String state, String county, int fips)
  {
    synchronized(countyToFips)
    {
      if (!countyToFips.containsKey(state))
      {
        countyToFips.put(state, new TreeMap<String, Integer>());
      }

      if (countyToFips.get(state).containsKey(county)) return;

      countyToFips.get(state).put(county, fips);
      synchronized(fips_lookup)
      {
        fips_lookup.put(fips, state +","+county);
      }


    }

  }

  protected int assignFips(String state, String county)
  {
    long seed = state.hashCode() * 87 + county.hashCode();
    Random rnd = new Random(seed);

    return rnd.nextInt(100000) + 100000;

  }

  public class UpdateThread extends PeriodicThread
  {
    public UpdateThread()
    {
      super(600000);
      
      config.require("data_path");
    }

    private boolean first_run=true;

    public void runPass() throws Exception
    {

      TreeMap<Integer, LocationData> m = new TreeMap<>();

      m.putAll(parseFile( new File(config.get("data_path"), "us-states.csv")));

      LocationData us = LocationData.merge(m.values());

      m.put(US_FIPS, us);

      List<File> county_paths = new LinkedList<>();
      county_paths.add(new File(config.get("data_path"), "us-counties-2020.csv"));
      county_paths.add(new File(config.get("data_path"), "us-counties-2021.csv"));
      county_paths.add(new File(config.get("data_path"), "us-counties-2022.csv"));

			m.putAll(parseFile( county_paths));


      location_data_map = ImmutableMap.copyOf(m);

      TreeMap<Integer, LocationData> m_avg = new TreeMap<>();
      for(Map.Entry<Integer,LocationData> me : m.entrySet())
      {
        m_avg.put(me.getKey(), me.getValue().getAverage());
      }
      location_data_avg_map = ImmutableMap.copyOf(m_avg);

      updatePopMap(new File(config.get("pop_path")));

      if (first_run)
      {
        System.out.println("First db update complete");
        first_run=false;
      }
      

    }

    protected void updatePopMap(File path)
      throws Exception
    {
      List<Map<String, String> > raw = CSVReader.readCSV(new FileInputStream(path));
      TreeMap<Integer, Long> pop_map = new TreeMap<>();

      for(Map<String, String> item : raw)
      {
        int state_fips = Integer.parseInt(item.get("STATE"));
        int county_fips = Integer.parseInt(item.get("COUNTY"));
        int fips = state_fips * 1000 + county_fips;
        long pop = Long.parseLong(item.get("POPESTIMATE2019"));
        if (county_fips == 0)
        {
          addPop(state_fips, pop, pop_map);
          addPop(US_FIPS, pop, pop_map);
        }
        addPop(fips, pop, pop_map);
      }

      pop_map.put(72, 3195000L); // Puerto Rico

      pop_map.put(78, 107268L); // Virgin Islands
      pop_map.put(66, 164229L); // Guam
      pop_map.put(69, 55144L); // Northern Mariana Islands
      pop_map.put(60, 55465L); // Northern Mariana Islands
      pop_map.put(147797, 8200000L); // NYC


      pop_by_fips = ImmutableMap.copyOf(pop_map);

      //logger.info("US Pop: " + pop_by_fips.get(US_FIPS));
      //logger.info("WA Pop: " + pop_by_fips.get(53));
      //logger.info("WA/King Pop: " + pop_by_fips.get(53033));

    }
    protected void addPop(int fips, long pop, Map<Integer, Long> map)
    {
      if (!map.containsKey(fips)) map.put(fips, 0L);

      map.put(fips, map.get(fips) + pop);

    }

   protected Map<Integer, LocationData> parseFile(File path)
      throws Exception
    {
      return parseFile(ImmutableList.of(path));
    }
    protected Map<Integer, LocationData> parseFile(List<File> paths)
      throws Exception
    {

      List<Map<String, String> > raw = new LinkedList<>();
      for(File p : paths)
      {
        raw.addAll(CSVReader.readCSV(new FileInputStream(p)));
      }

      Map<Integer, LocationData> m = new TreeMap<>();

      for(Map<String, String> item : raw)
      {
        int fips = Integer.parseInt(item.get("fips"));
        String state = item.get("state");
        String county = item.get("county");

        if (fips == -1) fips = assignFips(state, county);

        addDate(item.get("date"));

        if (county == null)
        {
          addState(state, fips);
        }
        else
        {
          county = county.replaceAll("'","");
          addCounty(state, county, fips);
        }

        JSONObject js = new JSONObject();

        for(String k : item.keySet())
        {
          String v = item.get(k);
          try
          {
            long n = Long.parseLong(v);
            js.put(k, n);
          }
          catch(Exception e)
          {
            js.put(k, v);
          }
        }

        if (!m.containsKey(fips))
        {
          m.put(fips, new LocationData());
        }
        m.get(fips).addData(js);
        
      }


      return m;

    }

  }

  public static class LocationData
  {
    
    private TreeMap<String, JSONObject> date_map = new TreeMap<>();

    public void addData(JSONObject json)
    {
      
      date_map.put((String)json.get("date"), json);
    }

    public LocationData getAverage()
    {
      LocationData avg = new LocationData();
      LinkedList<JSONObject> last_week = new LinkedList<>();
      for(String date : date_map.keySet())
      {
        last_week.add(date_map.get(date));
        while (last_week.size() > 8) last_week.removeFirst();
        double c = 0.0;
        double d = 0.0;
        for(JSONObject o : last_week)
        {
          c += Double.parseDouble(o.get("cases").toString());
          d += Double.parseDouble(o.get("deaths").toString());

        }
        c = c / last_week.size();
        d = d / last_week.size();

        JSONObject s = new JSONObject();
        s.put("cases", c);
        s.put("deaths", d);
        s.put("date", date);

        avg.addData(s);


      }
      return avg;

    }

    public static LocationData merge(Collection<LocationData> lst)
    {
      TreeSet<String> dates = new TreeSet<>(); 

      for(LocationData ld : lst)
      {
        dates.addAll(ld.date_map.keySet());

      }
      LocationData res = new LocationData();

      for(String date : dates)
      {
        double cases =0;
        double deaths = 0;

        for(LocationData ld : lst)
        {
          if (ld.date_map.containsKey(date))
          {
            double c = Double.parseDouble( ld.date_map.get(date).get("cases").toString());
            double d = Double.parseDouble( ld.date_map.get(date).get("deaths").toString());
            cases += c;
            deaths += d;

          }

        }

        JSONObject sum = new JSONObject();
        sum.put("cases", cases);
        sum.put("deaths", deaths);
        sum.put("date", date);

        res.addData(sum);

      }

      return res;

    }

  }


  public class WebServer
  {
    public WebServer() throws Exception
    {
      config.require("web_port");
      int port = config.getInt("web_port");
      HttpServer http_server;

      InetSocketAddress listen = new InetSocketAddress(port);
      http_server = HttpServer.create(listen, 16);
      http_server.createContext("/", new RootHandler());
      http_server.setExecutor(TaskMaster.getBasicExecutor(64,"web_server"));
      http_server.start();
    }

  }

  public class RootHandler implements HttpHandler
  { 
    public void handle(HttpExchange t) throws IOException 
    {
      URI uri = t.getRequestURI();
      logger.info("Web request: " + uri);

      TreeMap<String, String> options= new TreeMap<>();

			ArrayList<String> tokens = tokenizePath(uri, options);
			logger.info("Tokens: " + tokens);
      ByteArrayOutputStream b_out = new ByteArrayOutputStream();
      PrintStream print_out = new PrintStream(b_out);
			int code = 200;
			String mime_type = "text/plain";


      try
      {

			if (tokens.size() == 0)
      {
        mime_type="text/html";
        print_out.println("<H2>Hello</H2>");
        print_out.println("Data from <a href='https://github.com/nytimes/covid-19-data'>New York Times Covid 19 Data</a>");

        print_out.println("<li><a href='list_state'>list_state</a></li>");
        print_out.println("<li><a href='list_county'>list_county</a></li>");
        print_out.println("<li><a href='stats'>stats</a></li>");
        print_out.println("<li><a href='stats/Washington'>stats/Washington</a></li>");
        print_out.println("<li><a href='stats/Washington/King'>stats/Washington/King</a></li>");
        print_out.println("<li><a href='chart_data/cases/Washington,King/New%20York'>chart_data/cases/Washington,King/New%20York</a></li>");
        print_out.println("<li><a href='chart_data/deaths/Washington,King/New%20York'>chart_data/deaths/Washington,King/New%20York</a></li>");
        print_out.println("<p>chart_data returns data in a json array suitable for Google Chart google.visualization.arrayToDataTable</p>");
        print_out.println("<li><a href='worst'>Worst by deaths per capita</a><li>");
        print_out.println("<li><a href='worst/5'>Worst 5 by deaths per capita</a><li>");


      }
      else
      {
        String cmd = tokens.get(0);
        if (cmd.equals("list_state"))
        {
          JSONObject res = new JSONObject();

          JSONArray state_list = new JSONArray();
          synchronized(stateToFips)
          {
            res.put("states", stateToFips.keySet());
          }

          print_out.println(res);
          mime_type="application/json";
        }
        else if(cmd.equals("list_county"))
        {
          mime_type="application/json";
          JSONObject res = new JSONObject();
          synchronized(countyToFips)
          {
            for(String state : countyToFips.keySet())
            {
              res.put(state, countyToFips.get(state).keySet());
            }
          }
          print_out.println(res);
        }
        else if(cmd.equals("stats"))
        {
          if (tokens.size() == 1)
          {
            print_out.println("Put /STATE/COUNTY or /STATE on the end of the url.");
          }
          else
          {
            String state = tokens.get(1);
            String county = null;
            if (tokens.size() ==3)
            {
              county = tokens.get(2);
            }
            int fips = -1;
            if (county==null)
            {
              synchronized(stateToFips)
              {
                fips = stateToFips.get(state);
              }
            }
            else
            {
              synchronized(countyToFips)
              {
                fips = countyToFips.get(state).get(county);
              }
            }

            mime_type="application/json";
            JSONObject res = new JSONObject();
            res.put("stats", location_data_map.get(fips).date_map.values());

            print_out.println(res);
          }


        }
        else if (cmd.equals("worst"))
        {
          mime_type = "application/json";
          JSONObject res = new JSONObject();
          int n =10;
          if (tokens.size() > 1)
          {
            n = Integer.parseInt(tokens.get(1));
          }

          TreeSet<Integer> fips_state_set = new TreeSet<>();
          TreeSet<Integer> fips_county_set = new TreeSet<>();

          synchronized(stateToFips)
          {
            fips_state_set.addAll( stateToFips.values()); 
          }
          synchronized(countyToFips)
          {
            for(String state : countyToFips.keySet())
            {
              fips_county_set.addAll( countyToFips.get(state).values() );
            }
          }

          TreeMap<Integer, Double> state_map = getDeathByPop(fips_state_set);          
          TreeMap<Integer, Double> county_map = getDeathByPop(fips_county_set);

          res.put("state", filterHighest(state_map, n));
          res.put("county", filterHighest(county_map, n));

          print_out.println(res);
        }
        else if (cmd.equals("chart_data"))
        {
          mime_type = "application/json";
          JSONArray res = new JSONArray();

          String type = tokens.get(1);

          boolean include_delta = false;
          boolean pop_normal = false;
          if (options.containsKey("include_delta"))
          {
            if (options.get("include_delta").equals("true"))
            {
              include_delta=true;
            }
          }

          if (options.containsKey("pop_norm"))
          {
            if (options.get("pop_norm").equals("true"))
            {
              pop_normal=true;
            }
          }
          

          JSONArray label = new JSONArray();
          label.add("Date");
          ArrayList<Integer> location_fips=new ArrayList<Integer>();
          TreeMap<Integer, String> fips_to_location=new TreeMap<>();


          for(int n=2; n<tokens.size(); n++)
          {
            String loc = tokens.get(n);
            String state =null;
            String county = null;
            int fips = -1;
            String label_str = null;
            if (loc.equals("US"))
            {
              fips= US_FIPS;
              label_str = "US";

            }
            else if (loc.indexOf(",") >= 0)
            {
              state = loc.split(",")[0];
              county = loc.split(",")[1];
              label_str = state+ "," +county;
              fips = countyToFips.get(state).get(county);

            }
            else
            {
              state = loc;
              label_str = state;
              fips = stateToFips.get(state);
            }
            label.add(label_str);
            if (include_delta)
            {
              label.add(label_str + " delta");
            }
            location_fips.add(fips);
            fips_to_location.put(fips, label_str);
          }
          
          res.add(label);

          TreeMap<Integer, Double> last_val=new TreeMap<>();
          for(Integer fips : location_fips)
          {
            last_val.put(fips, 0.0);
          }

          synchronized(dates)
          {
            for(String date : dates)
            {
              JSONArray row = new JSONArray();
              row.add(date);
              for(Integer fips : location_fips)
              {
                JSONObject js = location_data_avg_map.get(fips).date_map.get(date);
                double v = 0.0;
                if (js == null)
                {
                  v = 0.0;
                }
                else
                {
                  double c = Double.parseDouble(js.get(type).toString());
                  if (pop_normal)
                  {
                    if (!pop_by_fips.containsKey(fips))
                    {
                      throw new Exception("No population data for: " + fips + " - " + fips_to_location.get(fips));
                    }
                    double pop = pop_by_fips.get(fips);
                    double cd = c;
                    v = cd * 100000.0 / pop;

                  }
                  else
                  {
                    v = c;
                  }
                }
                row.add(v);
                double last = last_val.get(fips);
                double delta = v - last;
                if (include_delta)
                {
                  row.add(delta);
                }
                
                
                last_val.put(fips, v);

              }
              res.add(row);

            }

          }

          print_out.println(res);

        }
        else
        {
          code =404;
          print_out.println("unknown command");
        }


      }
			
      }
      catch(Throwable e)
      {
        code=500;
        mime_type="text/plain";
        print_out.println("Error - " + e);
        e.printStackTrace();
      }


      t.getResponseHeaders().add("Cache-Control","max-age=3600");
			t.getResponseHeaders().add("Content-type",mime_type);
      t.getResponseHeaders().add("Access-Control-Allow-Origin","*");
      byte[] data = b_out.toByteArray();
      t.sendResponseHeaders(code, data.length);
      OutputStream out = t.getResponseBody();
      out.write(data);
      out.close();
			
    }

  }

  private ArrayList<String> tokenizePath(URI uri, TreeMap<String,String> options)
  {

    StringTokenizer stok = new StringTokenizer(uri.getPath(), "/");
    ArrayList<String> tokens = new ArrayList<>();

    while(stok.hasMoreTokens())
    {
      tokens.add( stok.nextToken());
    }

    if (uri.getQuery()!=null)
    {
      String option_str = uri.getQuery();
      StringTokenizer opt_out = new StringTokenizer(option_str, "&");
      while(opt_out.hasMoreTokens())
      {
        String line = opt_out.nextToken();
        int eq=line.indexOf('=');
        if (eq > 0)
        {
          String key = line.substring(0, eq);
          String val = line.substring(eq+1);
          options.put(key,val);
        }
      }

      System.out.println("Options:" + options);

    }

    return tokens;
  }


  private TreeMap<Integer, Double> getDeathByPop(Collection<Integer> fips_set)
  {
    TreeMap<Integer, Double> m = new TreeMap<>();
    for(int fips : fips_set)
    {
      try
      {
        double deaths = (Long)location_data_map.get(fips).date_map.lastEntry().getValue().get("deaths");

        if (!pop_by_fips.containsKey(fips))
        { 
          //System.out.println("No pop for fips: " + fips_lookup.get(fips));
        }
        else
        {
          double pop = pop_by_fips.get(fips);
          double rate = deaths * 100000.0 / pop;
          m.put(fips, rate);
        }
      }
      catch(Throwable t)
      {
        System.out.println("Exception on fips- " + fips + ": " + t);
        t.printStackTrace();
      }
      

    }
    return m;
  }

  private JSONObject filterHighest(Map<Integer, Double> fips_to_val, int n)
  {
    Random rnd = new Random();
    TreeMap<Double, Integer> rev_map = new TreeMap<>();

    for(int fips : fips_to_val.keySet())
    {
      double  v = fips_to_val.get(fips) + rnd.nextDouble()/1e6;
      rev_map.put(v, fips);
    }

    JSONObject result = new JSONObject();
    JSONArray list = new JSONArray();
    JSONObject values = new JSONObject();

    for(int i=0; (i<n) && (rev_map.size()>0); i++)
    {
      int fips = rev_map.pollLastEntry().getValue();
      String name = fips_lookup.get(fips);

      list.add(name);
      values.put(name, fips_to_val.get(fips));
    }

    result.put("ordered_list", list);
    result.put("values", values);

    return result;

  }

}
