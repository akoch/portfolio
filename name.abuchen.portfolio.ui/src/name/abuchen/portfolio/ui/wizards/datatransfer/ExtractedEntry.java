package name.abuchen.portfolio.ui.wizards.datatransfer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.Extractor.Item;
import name.abuchen.portfolio.datatransfer.ImportAction;
import name.abuchen.portfolio.datatransfer.ImportAction.Context;
import name.abuchen.portfolio.datatransfer.ImportAction.Status;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Portfolio;

public class ExtractedEntry implements Context
{
    private final Extractor.Item item;

    private Boolean isImported = null;
    private Status.Code maxCode = Status.Code.OK;
    private List<Status> status = new ArrayList<>();
    private Context parentContext;

    private Account account;
    private Account secondaryAccount;
    private Portfolio portfolio;
    private Portfolio secondaryPortfolio;

    public ExtractedEntry(Item item, Context parentContext)
    {
        this.item = item;
        this.parentContext = parentContext;
    }

    public Extractor.Item getItem()
    {
        return item;
    }

    public void setImported(boolean isImported)
    {
        this.isImported = isImported;
    }

    public boolean isImported()
    {
        // do not import if explicitly excluded by the user
        if (isImported != null && !isImported.booleanValue())
            return false;

        // otherwise import if either the status is OK or the user explicitly
        // overrides warnings
        return maxCode == Status.Code.OK
                        || (maxCode == Status.Code.WARNING && isImported != null && isImported.booleanValue());
    }

    public void addStatus(ImportAction.Status status)
    {
        this.status.add(status);
        if (status.getCode().isHigherSeverityAs(maxCode))
            maxCode = status.getCode();
    }

    public Stream<Status> getStatus()
    {
        return this.status.stream();
    }

    public Account getAccount()
    {
        if (account == null)
        {
        	return parentContext.getAccount();
        }
        return account;
    }

    public Account getSecondaryAccount()
    {
        if (secondaryAccount == null)
        {
        	return parentContext.getSecondaryAccount();
        }
        return secondaryAccount;
    }

    public void setAccount(Account account)
    {
        this.account = account;
    }

    public void setSecondaryAccount(Account secondaryAccount)
    {
        this.secondaryAccount = secondaryAccount;
    }

    public Portfolio getPortfolio()
    {
        if (portfolio == null)
        {
        	return parentContext.getPortfolio();
        }
        return portfolio;
    }

    public Portfolio getSecondaryPortfolio()
    {
        if (secondaryPortfolio == null)
        {
        	return parentContext.getSecondaryPortfolio();
        }
        return secondaryPortfolio;
    }

    public void setPortfolio(Portfolio portfolio)
    {
        this.portfolio = portfolio;
    }

    public void setSecondaryPortfolio(Portfolio secondaryPortfolio)
    {
        this.secondaryPortfolio = secondaryPortfolio;
    }

    public Status.Code getMaxCode()
    {
        return maxCode;
    }

    public void clearStatus()
    {
        this.status.clear();
        this.maxCode = Status.Code.OK;
    }
}
