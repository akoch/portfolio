package name.abuchen.portfolio.online.impl;

import static name.abuchen.portfolio.online.impl.OnVistaHelper.*;

import java.io.IOException;
import java.net.URLEncoder;import java.nio.channels.spi.AsynchronousChannelProvider;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.jsoup.Jsoup;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.model.Exchange;
import name.abuchen.portfolio.model.LatestSecurityPrice;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.online.QuoteFeed;

public class OnVistaQuoteFeed implements QuoteFeed
{
    public static final String ID = "OnVista"; //$NON-NLS-1$

    private static final String HISTORICAL_SNAPSHOT_URL = "http://www.onvista.de/etf/ajax/snapshotHistory"; //$NON-NLS-1$
    
    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public String getName()
    {
        return Messages.LabelOnVista;
    }

    @Override
    public boolean updateLatestQuotes(List<Security> securities, List<Exception> errors)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean updateHistoricalQuotes(Security security, List<Exception> errors)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(Security security, LocalDate start, List<Exception> errors)
    {
//        datetimeTzStartRange:18.11.2015
//        timeSpan:1Y
//        codeResolution:1D
//        idNotation:56136187
        List<LatestSecurityPrice> latestSecurityPrices = new LinkedList<>();
        String url = String.format(HISTORICAL_SNAPSHOT_URL);
        try
        {
            String jsonBody = Jsoup.connect(url)
            .data("datetimeTzStartRange", start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
            .data("timeSpan", "1Y")
            .data("codeResolution", "1D")
            .data("idNotation", "56136187")
            .ignoreContentType(true)
            .post().body().text();
            
            JSONArray jsonObject = (JSONArray) JSONValue.parse(jsonBody);
            
            jsonObject.forEach(json -> {
                try
                {
                    JSONObject priceObject = (JSONObject) json;
                    JSONObject datetimeLast = (JSONObject)priceObject.get("datetimeLast"); 
                    
                    LatestSecurityPrice securityPrice = new LatestSecurityPrice(asDate(datetimeLast.get("localTime").toString()), asPrice(priceObject.get("last").toString()));
                    securityPrice.setHigh(asPrice(priceObject.get("high").toString()));
                    securityPrice.setLow(asPrice(priceObject.get("low").toString()));
                    securityPrice.setVolume(asNumber(priceObject.get("totalVolume").toString()));
                    
                    securityPrice.setPreviousClose(securityPrice.getValue());
                    latestSecurityPrices.add(securityPrice);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return latestSecurityPrices;
    }

    @Override
    public List<LatestSecurityPrice> getHistoricalQuotes(String response, List<Exception> errors)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Exchange> getExchanges(Security subject, List<Exception> errors)
    {
        // TODO Auto-generated method stub
        return null;
    }

}
