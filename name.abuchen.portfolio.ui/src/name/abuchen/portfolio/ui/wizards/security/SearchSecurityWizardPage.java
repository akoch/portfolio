package name.abuchen.portfolio.ui.wizards.security;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.Factory;
import name.abuchen.portfolio.online.SecuritySearchProvider;
import name.abuchen.portfolio.online.SecuritySearchProvider.ResultItem;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;

public class SearchSecurityWizardPage extends WizardPage
{
    private Client client;

    private ResultItem item;

    private String label;

    public SearchSecurityWizardPage(Client client, String label)
    {
        super("searchpage"); //$NON-NLS-1$
        this.label = label;
        setTitle(Messages.SecurityMenuAddNewSecurity);
        setDescription(MessageFormat.format(Messages.SecurityMenuAddNewSecurityDescription, label));

        this.client = client;
    }

    @Override
    public void createControl(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NULL);
        GridLayoutFactory.fillDefaults().numColumns(1).applyTo(container);

        final Text searchBox = new Text(container, SWT.BORDER | SWT.SINGLE);
        searchBox.setText(""); //$NON-NLS-1$
        searchBox.setFocus();
        GridDataFactory.fillDefaults().grab(true, false).applyTo(searchBox);

        final TableViewer resultTable = new TableViewer(container, SWT.FULL_SELECTION);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(resultTable.getControl());

        TableColumn column = new TableColumn(resultTable.getTable(), SWT.NONE);
        column.setText(Messages.ColumnSymbol);
        column.setWidth(60);

        column = new TableColumn(resultTable.getTable(), SWT.NONE);
        column.setText(Messages.ColumnName);
        column.setWidth(140);

        column = new TableColumn(resultTable.getTable(), SWT.NONE);
        column.setText(Messages.ColumnISIN);
        column.setWidth(100);

        column = new TableColumn(resultTable.getTable(), SWT.RIGHT);
        column.setText(Messages.ColumnLastTrade);
        column.setWidth(60);

        column = new TableColumn(resultTable.getTable(), SWT.NONE);
        column.setText(Messages.ColumnSecurityType);
        column.setWidth(60);

        column = new TableColumn(resultTable.getTable(), SWT.NONE);
        column.setText(Messages.ColumnSecurityExchange);
        column.setWidth(60);

        resultTable.getTable().setHeaderVisible(true);
        resultTable.getTable().setLinesVisible(true);

        final Set<String> existingSymbols = new HashSet<String>();
        for (Security s : client.getSecurities()) {
            String tickerSymbol = s.getTickerSymbol();
            if (tickerSymbol != null) {
                existingSymbols.add(tickerSymbol);
            }
        }
        
        final Set<String> existingWkns = new HashSet<String>();
        for (Security s : client.getSecurities()) {
            String wkn = s.getWkn();
            if (wkn != null) {
                existingWkns.add(wkn);
            }
        }
        
        resultTable.setLabelProvider(new ResultItemLabelProvider(existingSymbols, existingWkns));
        resultTable.setContentProvider(ArrayContentProvider.getInstance());

        searchBox.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent e)
            {
                // don't forward to the default button
                e.doit = false;
            }
        });

        searchBox.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetDefaultSelected(SelectionEvent event)
            {
                doSearch(searchBox.getText(), resultTable);
            }
        });

        resultTable.addSelectionChangedListener(new ISelectionChangedListener()
        {
            @Override
            public void selectionChanged(SelectionChangedEvent event)
            {
                item = (ResultItem) ((IStructuredSelection) event.getSelection()).getFirstElement();
                setPageComplete(item != null && !existingSymbols.contains(item.getSymbol()) && !existingWkns.contains(item.getWkn()));
            }
        });

        setControl(container);
    }

    public ResultItem getResult()
    {
        return item;
    }

    private void doSearch(String query, TableViewer resultTable)
    {
        try
        {
            getContainer().run(true, false, m -> {
                try
                {
                    Optional<SecuritySearchProvider> provider = Factory.getSearchProvider().stream().filter(p -> p.getName().equals(label)).findFirst();
                    if(provider.isPresent()) {
                        List<ResultItem> result = provider.get().search(query);
                        Display.getDefault().asyncExec(() -> resultTable.setInput(result));
                    }
                }
                catch (IOException e)
                {
                    PortfolioPlugin.log(e);
                    Display.getDefault().asyncExec(() -> setErrorMessage(e.getMessage()));
                }
            });
        }
        catch (InvocationTargetException | InterruptedException e)
        {
            PortfolioPlugin.log(e);
        }
    }

    private static class ResultItemLabelProvider extends LabelProvider
                    implements ITableLabelProvider, ITableColorProvider
    {
        private final Set<String> symbols;
        private final Set<String> existingWkns;

        public ResultItemLabelProvider(Set<String> symbols, Set<String> existingWkns)
        {
            this.symbols = symbols;
            this.existingWkns = existingWkns;
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            ResultItem item = (ResultItem) element;
            switch (columnIndex)
            {
                case 0:
                    return item.getSymbol();
                case 1:
                    return item.getName();
                case 2:
                    return item.getIsin();
                case 3:
                    if (item.getLastTrade() != 0)
                        return Values.Quote.format(item.getLastTrade());
                    else
                        return null;
                case 4:
                    return item.getType();
                case 5:
                    return item.getExchange();
                default:
                    throw new IllegalArgumentException(String.valueOf(columnIndex));
            }
        }

        @Override
        public Color getForeground(Object element, int columnIndex)
        {
            ResultItem item = (ResultItem) element;

            if (item.getSymbol() == null)
                return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
            else if (symbols.contains(item.getSymbol()) || existingWkns.contains(item.getWkn()))
                return Display.getCurrent().getSystemColor(SWT.COLOR_GRAY);
            else
                return null;
        }

        @Override
        public Color getBackground(Object element, int columnIndex)
        {
            return null;
        }
    }

}
