package name.abuchen.portfolio.online.impl;

import static name.abuchen.portfolio.online.impl.YahooHelper.asPrice;
import static name.abuchen.portfolio.online.impl.YahooHelper.stripQuotes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.SecuritySearchProvider;

public class OnVistaSearchProvider implements SecuritySearchProvider
{
    private static final String SEARCH_URL = "http://www.onvista.de/onvista/boxes/assetSearch.json?doSubmit=Suchen&portfolioName=&searchValue=%s"; //$NON-NLS-1$

    private static final ThreadLocal<DecimalFormat> FMT_INDEX = new ThreadLocal<DecimalFormat>()
    {
        protected DecimalFormat initialValue()
        {
            return new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.GERMANY)); //$NON-NLS-1$
        }
    };

    /* package */ static class Result
    {
        private String wkn;
        private String name;
        private String type;
        private String exchange;

        public static Result from(JSONObject json)
        {
            String wkn = json.get("nsin").toString(); //$NON-NLS-1$
            String name = json.get("name").toString(); //$NON-NLS-1$
            String type = json.get("type").toString(); //$NON-NLS-1$
            
//            String exchange = json.get("exchDisp").toString(); //$NON-NLS-1$
            return new Result(wkn, name, type, null);
        }

        private Result(String wkn, String name, String type, String exchange)
        {
            this.wkn = wkn;
            this.name = name;
            this.type = type;
            this.exchange = exchange;
        }

        public String getWkn()
        {
            return wkn;
        }

        public String getName()
        {
            return name;
        }

        public String getType()
        {
            return type;
        }

        public String getExchange()
        {
            return exchange;
        }
    }
    
    public static class OnVistaResultItem extends ResultItem
    {
        @Override
        public void applyTo(Security security)
        {
            super.applyTo(security);
            security.setFeed(OnVistaQuoteFeed.ID);
        }

        public static ResultItem from(Result r)
        {
            OnVistaResultItem item = new OnVistaResultItem();
            item.setWkn(r.getWkn());
            item.setName(r.getName());
            item.setExchange(r.getExchange());
            item.setType(r.getType());
            return item;
        }
    }

    @Override
    public String getName()
    {
        return Messages.LabelOnVista;
    }

    @Override
    public List<ResultItem> search(String query) throws IOException
    {
        // search both the HTML page as well as the symbol search
        String url = String.format(SEARCH_URL, URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
        String jsonBody = Jsoup.connect(url)
        .data("searchValue", query)
        .ignoreContentType(true)
        .get().body().text();
        
       JSONObject jsonObject = (JSONObject) JSONValue.parse(jsonBody);
        
        List<ResultItem> answer = extractFrom(jsonObject);
        addSymbolSearchResults(answer, query);

       if (answer.size() >= 20)
        {
            ResultItem item = new OnVistaResultItem();
            item.setName(Messages.MsgMoreResultsAvailable);
            answer.add(item);
        }

        return answer;
    }

    private void addSymbolSearchResults(List<ResultItem> answer, String query) throws IOException
    {
        Set<String> existingSymbols = answer.stream().map(r -> r.getSymbol()).collect(Collectors.toSet());

//        new YahooSymbolSearch().search(query)//
//                        .filter(r -> !existingSymbols.contains(r.getSymbol()))
//                        .forEach(r -> answer.add(OnVistaResultItem.from(r)));
    }

    private JSONObject find(JSONObject parent, String... objects) {
        JSONObject jsonObject = parent;
        for (String objectName : objects)
        {
            jsonObject = (JSONObject) jsonObject.get(objectName);
        }
        return jsonObject;
    }
    
    /* protected */List<ResultItem> extractFrom(JSONObject jsonObject) throws IOException
    {
        List<ResultItem> answer = new ArrayList<ResultItem>();

        JSONObject onvistaObject = find(jsonObject , "onvista", "results");
        
        JSONArray assets = (JSONArray) onvistaObject.get("asset");
        
        
        assets.forEach(a -> {
            JSONObject asset = (JSONObject) a;
            answer.add(OnVistaResultItem.from(Result.from(asset)));
        });
        
        System.out.println(assets);
        
//        Elements tables = jsonObject.getElementsByAttribute("SUMMARY"); //$NON-NLS-1$
//
//        for (Element table : tables)
//        {
//            if (!"YFT_SL_TABLE_SUMMARY".equals(table.attr("SUMMARY"))) //$NON-NLS-1$ //$NON-NLS-2$
//                continue;
//
//            Elements rows = table.select("> tbody > tr"); //$NON-NLS-1$
//
//            for (Element row : rows)
//            {
//                Elements cells = row.select("> td"); //$NON-NLS-1$
//
//                if (cells.size() != 6)
//                    continue;
//
//                ResultItem item = new OnVistaResultItem();
//
//                item.setSymbol(cells.get(0).text());
//                item.setName(cells.get(1).text());
//                item.setIsin(cells.get(2).text());
//
//                // last trace
//                String lastTrade = cells.get(3).text();
//                if (!"NaN".equals(lastTrade)) //$NON-NLS-1$
//                    item.setLastTrade(parseIndex(lastTrade));
//
//                item.setType(cells.get(4).text());
//                item.setExchange(cells.get(5).text());
//
//                answer.add(item);
//            }
//        }

        return answer;
    }

    private long parseIndex(String text) throws IOException
    {
        try
        {
            Number q = FMT_INDEX.get().parse(text);
            return (long) (q.doubleValue() * Values.Quote.factor());
        }
        catch (ParseException e)
        {
            throw new IOException(e);
        }
    }
}
