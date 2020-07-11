package duck.covid19;

import java.util.Scanner;
import java.io.InputStream;
import java.util.List;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class CSVReader
{
  public static List<Map<String, String> > readCSV(InputStream in)
      throws Exception
  {
    return readCSV(in, 0);
  }
  public static List<Map<String, String> > readCSV(InputStream in, int skip_lines)
      throws Exception
  {
    Scanner scan = new Scanner(in);

    for(int i=0; i<skip_lines; i++)
    {
      scan.nextLine();
    }
    ArrayList<String> col_names=getLineAsArray(scan.nextLine());

    LinkedList<Map<String, String> > res = new LinkedList<Map<String, String> >();

    while(scan.hasNextLine())
    {
      TreeMap<String, String> map = new TreeMap<String, String>();
      ArrayList<String> lst = getLineAsArray(scan.nextLine());

      for(int i=0; i<lst.size(); i++)
      {
        String name = col_names.get(i);
        String value = lst.get(i);
        map.put(name, value);
      }

      res.add(map);

    }

    return res;


  }

  public static ArrayList<String> getLineAsArray(String line)
  {
    line=line.replace(",,",",-1,");
    line=line.replace(",,",",-1,");
    line=line.replace(",,",",-1,");
    line=line.replace(",,",",-1,");
    line=line.replace(",,",",-1,");
    if (line.endsWith(",")) line = line + "-1";
    StringTokenizer stok=new StringTokenizer(line, ",");
    ArrayList<String> lst = new ArrayList<String>();

    while(stok.hasMoreTokens())
    {
      lst.add(stok.nextToken().trim());
    }
    return lst;
  }

}
