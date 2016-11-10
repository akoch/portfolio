package name.abuchen.portfolio.ui.wizards.security;

import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.graphics.Image;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.online.SecuritySearchProvider.ResultItem;
import name.abuchen.portfolio.ui.Images;

public class SearchSecurityWizard extends Wizard
{
    private final Client client;
    private SearchSecurityWizardPage page;
    private String label;
    private String feed;

    public SearchSecurityWizard(Client client, String label, String feed)
    {
        this.client = client;
        this.label = label;
        this.feed = feed;

        this.setNeedsProgressMonitor(true);
    }
    
    @Override
    public Image getDefaultPageImage()
    {
        return Images.BANNER.image();
    }

    @Override
    public void addPages()
    {
        addPage(page = new SearchSecurityWizardPage(client, this.label));
    }

    public Security getSecurity()
    {
        ResultItem item = page.getResult();

        if (item == null)
            return null;

        Security security = new Security();
        security.setName(item.getName());
        security.setTickerSymbol(item.getSymbol());
        security.setIsin(item.getIsin());
        security.setWkn(item.getWkn());
        security.setFeed(this.feed);

        return security;
    }

    @Override
    public boolean performFinish()
    {
        return page.getResult() != null;
    }
}
