package name.abuchen.portfolio.ui.wizards.datatransfer;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.Extractor.Item;
import name.abuchen.portfolio.datatransfer.ImportAction;
import name.abuchen.portfolio.datatransfer.ImportAction.Context;
import name.abuchen.portfolio.datatransfer.actions.CheckCurrenciesAction;
import name.abuchen.portfolio.datatransfer.actions.CheckValidTypesAction;
import name.abuchen.portfolio.datatransfer.actions.DetectDuplicatesAction;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.AccountTransferEntry;
import name.abuchen.portfolio.model.Annotated;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Named;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransferEntry;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.AbstractClientJob;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.util.FormDataFactory;
import name.abuchen.portfolio.ui.util.LabelOnly;
import name.abuchen.portfolio.ui.wizards.AbstractWizardPage;

public class ReviewExtractedItemsPage extends AbstractWizardPage implements Context
{
    /* package */static final String PAGE_ID = "reviewitems"; //$NON-NLS-1$

    private static final String IMPORT_TARGET = "import-target"; //$NON-NLS-1$
    private static final String IMPORT_TARGET_PORTFOLIO = IMPORT_TARGET + "-portfolio-"; //$NON-NLS-1$
    private static final String IMPORT_TARGET_ACCOUNT = IMPORT_TARGET + "-account-"; //$NON-NLS-1$

    private TableViewer tableViewer;
    private TableViewer errorTableViewer;

    private Label lblPrimaryPortfolio;
    private ComboViewer primaryPortfolio;
    private Label lblSecondaryPortfolio;
    private ComboViewer secondaryPortfolio;
    private Label lblPrimaryAccount;
    private ComboViewer primaryAccount;
    private Label lblSecondaryAccount;
    private ComboViewer secondaryAccount;
    private Button cbConvertToDelivery;

    private final Client client;
    private final Extractor extractor;
    private final IPreferenceStore preferences;
    private List<File> files;

    private List<ExtractedEntry> allEntries = new ArrayList<ExtractedEntry>();

    public ReviewExtractedItemsPage(Client client, Extractor extractor, IPreferenceStore preferences, List<File> files)
    {
        super(PAGE_ID);

        this.client = client;
        this.extractor = extractor;
        this.preferences = preferences;
        this.files = files;

        setTitle(extractor.getLabel());
        setDescription(Messages.PDFImportWizardDescription);
    }

    public List<ExtractedEntry> getEntries()
    {
        return allEntries;
    }

    public Portfolio getPortfolio()
    {
        return (Portfolio) ((IStructuredSelection) primaryPortfolio.getSelection()).getFirstElement();
    }

    public Portfolio getSecondaryPortfolio()
    {
        return (Portfolio) ((IStructuredSelection) secondaryPortfolio.getSelection()).getFirstElement();
    }

    public Account getAccount()
    {
        return (Account) ((IStructuredSelection) primaryAccount.getSelection()).getFirstElement();
    }

    public Account getSecondaryAccount()
    {
        return (Account) ((IStructuredSelection) secondaryAccount.getSelection()).getFirstElement();
    }

    public boolean doConvertToDelivery()
    {
        return cbConvertToDelivery.getSelection();
    }

    @Override
    public IWizardPage getNextPage()
    {
        return null;
    }

    @Override
    public void createControl(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NULL);
        setControl(container);
        container.setLayout(new FormLayout());

        Composite targetContainer = new Composite(container, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(4).applyTo(targetContainer);

        lblPrimaryAccount = new Label(targetContainer, SWT.NONE);
        lblPrimaryAccount.setText(Messages.ColumnAccount);
        Combo cmbAccount = new Combo(targetContainer, SWT.READ_ONLY);
        primaryAccount = new ComboViewer(cmbAccount);
        primaryAccount.setContentProvider(ArrayContentProvider.getInstance());
        primaryAccount.setInput(client.getActiveAccounts());
        primaryAccount.addSelectionChangedListener(e -> checkEntriesAndRefresh(allEntries));

        lblSecondaryAccount = new Label(targetContainer, SWT.NONE);
        lblSecondaryAccount.setText(Messages.LabelTransferTo);
        lblSecondaryAccount.setVisible(false);
        Combo cmbAccountTarget = new Combo(targetContainer, SWT.READ_ONLY);
        secondaryAccount = new ComboViewer(cmbAccountTarget);
        secondaryAccount.setContentProvider(ArrayContentProvider.getInstance());
        secondaryAccount.setInput(client.getActiveAccounts());
        secondaryAccount.getControl().setVisible(false);
        secondaryAccount.addSelectionChangedListener(e -> tableViewer.refresh());

        lblPrimaryPortfolio = new Label(targetContainer, SWT.NONE);
        lblPrimaryPortfolio.setText(Messages.ColumnPortfolio);
        Combo cmbPortfolio = new Combo(targetContainer, SWT.READ_ONLY);
        primaryPortfolio = new ComboViewer(cmbPortfolio);
        primaryPortfolio.setContentProvider(ArrayContentProvider.getInstance());
        primaryPortfolio.setInput(client.getActivePortfolios());
        primaryPortfolio.addSelectionChangedListener(e -> checkEntriesAndRefresh(allEntries));

        lblSecondaryPortfolio = new Label(targetContainer, SWT.NONE);
        lblSecondaryPortfolio.setText(Messages.LabelTransferTo);
        lblSecondaryPortfolio.setVisible(false);
        Combo cmbPortfolioTarget = new Combo(targetContainer, SWT.READ_ONLY);
        secondaryPortfolio = new ComboViewer(cmbPortfolioTarget);
        secondaryPortfolio.setContentProvider(ArrayContentProvider.getInstance());
        secondaryPortfolio.setInput(client.getActivePortfolios());
        secondaryPortfolio.getControl().setVisible(false);
        secondaryPortfolio.addSelectionChangedListener(e -> tableViewer.refresh());

        preselectDropDowns();

        cbConvertToDelivery = new Button(container, SWT.CHECK);
        cbConvertToDelivery.setText(Messages.LabelConvertBuySellIntoDeliveryTransactions);

        Composite compositeTable = new Composite(container, SWT.NONE);
        Composite errorTable = new Composite(container, SWT.NONE);

        //
        // form layout
        //

        FormDataFactory.startingWith(targetContainer) //
                        .top(new FormAttachment(0, 0)).left(new FormAttachment(0, 0)).right(new FormAttachment(100, 0))
                        .thenBelow(cbConvertToDelivery) //
                        .thenBelow(compositeTable).right(targetContainer).bottom(new FormAttachment(70, 0)) //
                        .thenBelow(errorTable).right(targetContainer).bottom(new FormAttachment(100, 0));

        //
        // table & columns
        //

        TableColumnLayout layout = new TableColumnLayout();
        compositeTable.setLayout(layout);

        tableViewer = new TableViewer(compositeTable, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());

        Table table = tableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        ColumnViewerToolTipSupport.enableFor(tableViewer);
        addColumns(tableViewer, layout);
        attachContextMenu(table);

        layout = new TableColumnLayout();
        errorTable.setLayout(layout);
        errorTableViewer = new TableViewer(errorTable, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        errorTableViewer.setContentProvider(ArrayContentProvider.getInstance());

        table = errorTableViewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        addColumnsExceptionTable(errorTableViewer, layout);
    }

    private void preselectDropDowns()
    {
        // idea: generally one type of document (i.e. from the same bank) will
        // be imported into the same account

        List<Account> activeAccounts = client.getActiveAccounts();
        if (!activeAccounts.isEmpty())
        {
            String uuid = preferences.getString(IMPORT_TARGET_ACCOUNT + extractor.getClass().getSimpleName());

            // do not trigger selection listener (-> do not user #setSelection)
            primaryAccount.getCombo().select(IntStream.range(0, activeAccounts.size())
                            .filter(i -> activeAccounts.get(i).getUUID().equals(uuid)).findAny().orElse(0));
            secondaryAccount.getCombo().select(0);
        }

        List<Portfolio> activePortfolios = client.getActivePortfolios();
        if (!activePortfolios.isEmpty())
        {
            String uuid = preferences.getString(IMPORT_TARGET_PORTFOLIO + extractor.getClass().getSimpleName());
            // do not trigger selection listener (-> do not user #setSelection)
            primaryPortfolio.getCombo().select(IntStream.range(0, activePortfolios.size())
                            .filter(i -> activePortfolios.get(i).getUUID().equals(uuid)).findAny().orElse(0));
            secondaryPortfolio.getCombo().select(0);
        }
    }

    private void addColumnsExceptionTable(TableViewer viewer, TableColumnLayout layout)
    {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(Messages.ColumnErrorMessages);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                Exception e = (Exception) element;
                String text = e.getMessage();
                return text == null || text.isEmpty() ? e.getClass().getName() : text;
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnWeightData(100, true));
    }

    private void addColumns(TableViewer viewer, TableColumnLayout layout)
    {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(Messages.ColumnStatus);
        column.getColumn().setToolTipText(Messages.ColumnStatus);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public Image getImage(ExtractedEntry element)
            {
                Images image = null;
                switch (element.getMaxCode())
                {
                    case WARNING:
                        image = Images.WARNING;
                        break;
                    case ERROR:
                        image = Images.ERROR;
                        break;
                    case OK:
                    default:
                }
                return image != null ? image.image() : null;
            }

            @Override
            public String getText(ExtractedEntry entry)
            {
                return ""; //$NON-NLS-1$
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(22, true));

        column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(Messages.ColumnDate);
        column.getColumn().setToolTipText(Messages.ColumnDate);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public String getText(ExtractedEntry entry)
            {
                LocalDate date = entry.getItem().getDate();
                return date != null ? Values.Date.format(date) : null;
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(80, true));

        column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(Messages.ColumnTransactionType);
        column.getColumn().setToolTipText(Messages.ColumnTransactionType);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public String getText(ExtractedEntry entry)
            {
                return entry.getItem().getTypeInformation();
            }

            @Override
            public Image getImage(ExtractedEntry entry)
            {
                Annotated subject = entry.getItem().getSubject();
                if (subject instanceof AccountTransaction)
                    return Images.ACCOUNT.image();
                else if (subject instanceof PortfolioTransaction)
                    return Images.PORTFOLIO.image();
                else if (subject instanceof Security)
                    return Images.SECURITY.image();
                else if (subject instanceof BuySellEntry)
                    return Images.PORTFOLIO.image();
                else if (subject instanceof AccountTransferEntry)
                    return Images.ACCOUNT.image();
                else if (subject instanceof PortfolioTransferEntry)
                    return Images.PORTFOLIO.image();
                else
                    return null;
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(100, true));

        column = new TableViewerColumn(viewer, SWT.RIGHT);
        column.getColumn().setText(Messages.ColumnAmount);
        column.getColumn().setToolTipText(Messages.ColumnAmount);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public String getText(ExtractedEntry entry)
            {
                Money amount = entry.getItem().getAmount();
                return amount != null ? Values.Money.format(amount) : null;
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(80, true));

        column = new TableViewerColumn(viewer, SWT.RIGHT);
        column.getColumn().setText(Messages.ColumnShares);
        column.getColumn().setToolTipText(Messages.ColumnShares);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public String getText(ExtractedEntry entry)
            {
                return Values.Share.formatNonZero(entry.getItem().getShares());
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(80, true));

        column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(Messages.ColumnSecurity);
        column.getColumn().setToolTipText(Messages.ColumnSecurity);
        column.setLabelProvider(new FormattedLabelProvider()
        {
            @Override
            public String getText(ExtractedEntry entry)
            {
                Security security = entry.getItem().getSecurity();
                return security != null ? security.getName() : null;
            }
        });
        layout.setColumnData(column.getColumn(), new ColumnPixelData(250, true));

        if (client.getActivePortfolios().size() > 1)
        {
            column = new TableViewerColumn(viewer, SWT.NONE);
            column.getColumn().setText(Messages.ColumnPortfolio);
            column.getColumn().setToolTipText(Messages.ColumnPortfolio);
            column.setEditingSupport(new PortfolioEditingSupport(viewer, client.getActivePortfolios()));
            column.setLabelProvider(new FormattedLabelProvider()
            {
                @Override
                public String getText(ExtractedEntry entry)
                {
                    if (isPortfolioItem(entry))
                    {
                        return entry.getPortfolio().getName();
                    }
                    else
                    {
                        return null;
                    }
                }
            });
            layout.setColumnData(column.getColumn(), new ColumnPixelData(45, true));
        }

        if (client.getActiveAccounts().size() > 1)
        {
            column = new TableViewerColumn(viewer, SWT.NONE);
            column.getColumn().setText(Messages.ColumnAccount);
            column.getColumn().setToolTipText(Messages.ColumnAccount);
            column.setEditingSupport(new AccountEditingSupport(viewer, client.getActiveAccounts()));
            column.setLabelProvider(new FormattedLabelProvider()
            {
                @Override
                public String getText(ExtractedEntry entry)
                {
                    if (isAccountItem(entry))
                    {
                        return entry.getAccount().getName();
                    }
                    else
                    {
                        return null;
                    }
                }
            });
            layout.setColumnData(column.getColumn(), new ColumnPixelData(45, true));
        }
    }

    private void addTargetAccountColumn()
    {
        if (client.getActiveAccounts().size() > 1)
        {
            if (columnExists(Messages.ColumnAccountTarget)) { return; }
            TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.NONE);
            column.getColumn().setText(Messages.ColumnAccountTarget);
            column.getColumn().setToolTipText(Messages.ColumnAccountTarget);
            column.setEditingSupport(new TargetAccountEditingSupport(tableViewer, client.getActiveAccounts()));
            column.setLabelProvider(new FormattedLabelProvider()
            {
                @Override
                public String getText(ExtractedEntry entry)
                {
                    if (isAccountTransferItem(entry))
                    {
                        return entry.getSecondaryAccount().getName();
                    }
                    else
                    {
                        return null;
                    }
                }
            });
            Composite tableParent = tableViewer.getTable().getParent();
            TableColumnLayout layout = (TableColumnLayout) tableParent.getLayout();
            layout.setColumnData(column.getColumn(), new ColumnPixelData(70, true));
            tableViewer.refresh(true);
            tableParent.layout();
        }
    }

    private void addTargetPortfolioColumn()
    {
        if (client.getActivePortfolios().size() > 1)
        {
            if (columnExists(Messages.ColumnPortfolioTarget)) { return; }
            TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.NONE);
            column.getColumn().setText(Messages.ColumnPortfolioTarget);
            column.getColumn().setToolTipText(Messages.ColumnPortfolioTarget);
            column.setEditingSupport(new TargetPortfolioEditingSupport(tableViewer, client.getActivePortfolios()));
            column.setLabelProvider(new FormattedLabelProvider()
            {
                @Override
                public String getText(ExtractedEntry entry)
                {
                    if (isPortfolioTransferItem(entry))
                    {
                        return entry.getSecondaryPortfolio().getName();
                    }
                    else
                    {
                        return null;
                    }
                }
            });
            Composite tableParent = tableViewer.getTable().getParent();
            TableColumnLayout layout = (TableColumnLayout) tableParent.getLayout();
            layout.setColumnData(column.getColumn(), new ColumnPixelData(70, true));
            tableViewer.refresh(true);
            tableParent.layout();
        }
    }

    private boolean columnExists(String columnText)
    {
        for (TableColumn tableColumn : tableViewer.getTable().getColumns())
        {
            if (columnText.equals(tableColumn.getText()))
            {
                // column is already added
                return true;
            }
        }
        return false;
    }

    private void attachContextMenu(final Table table)
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(manager -> showContextMenu(manager));

        final Menu contextMenu = menuMgr.createContextMenu(table.getShell());
        table.setMenu(contextMenu);

        table.addDisposeListener(e -> {
            if (contextMenu != null && !contextMenu.isDisposed())
                contextMenu.dispose();
        });
    }

    private void showContextMenu(IMenuManager manager)
    {
        IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();

        boolean atLeastOneImported = false;
        boolean atLeastOneNotImported = false;

        for (Object element : selection.toList())
        {
            ExtractedEntry entry = (ExtractedEntry) element;

            // an entry will be imported if it is marked as to be
            // imported *and* not a duplicate
            atLeastOneImported = atLeastOneImported || entry.isImported();

            // an entry will not be imported if it marked as not to be
            // imported *or* if it is marked as duplicate
            atLeastOneNotImported = atLeastOneNotImported || !entry.isImported();
        }

        // provide a hint to the user why the entry is struck out
        if (selection.size() == 1)
        {
            ExtractedEntry entry = (ExtractedEntry) selection.getFirstElement();
            entry.getStatus() //
                            .filter(s -> s.getCode() != ImportAction.Status.Code.OK) //
                            .forEach(s -> {
                                Images image = s.getCode() == ImportAction.Status.Code.WARNING ? //
                                Images.WARNING : Images.ERROR;
                                manager.add(new LabelOnly(s.getMessage(), image.descriptor()));
                            });
        }

        if (atLeastOneImported)
        {
            manager.add(new Action(Messages.LabelDoNotImport)
            {
                @Override
                public void run()
                {
                    for (Object element : ((IStructuredSelection) tableViewer.getSelection()).toList())
                        ((ExtractedEntry) element).setImported(false);

                    tableViewer.refresh();
                }
            });
        }

        if (atLeastOneNotImported)
        {
            manager.add(new Action(Messages.LabelDoImport)
            {
                @Override
                public void run()
                {
                    for (Object element : ((IStructuredSelection) tableViewer.getSelection()).toList())
                        ((ExtractedEntry) element).setImported(true);

                    tableViewer.refresh();
                }
            });
        }

        if (client.getActivePortfolios().size() > 1)
        {
            MenuManager subMenu = new MenuManager(Messages.LabelAssignToPortfolio, null);
            for (Portfolio portfolio : client.getPortfolios())
            {
                subMenu.add(new AssignToPortfolioAction(portfolio));
            }
            manager.add(subMenu);
        }

        if (client.getActiveAccounts().size() > 1)
        {
            MenuManager subMenu = new MenuManager(Messages.LabelAssignToAccount, null);
            for (Account account : client.getActiveAccounts())
            {
                subMenu.add(new AssignToAccountAction(account));
            }
            manager.add(subMenu);
        }
    }

    @Override
    public void beforePage()
    {
        setTitle(extractor.getLabel());

        // clear all entries (if embedded into multi-page wizard)
        allEntries.clear();
        tableViewer.setInput(allEntries);
        errorTableViewer.setInput(Collections.emptyList());

        try
        {
            new AbstractClientJob(client, extractor.getLabel())
            {
                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    monitor.beginTask(Messages.PDFImportWizardMsgExtracting, files.size());

                    final List<Exception> errors = new ArrayList<Exception>();
                    List<ExtractedEntry> entries = extractor //
                                    .extract(files, errors).stream() //
                                    .map(i -> new ExtractedEntry(i, ReviewExtractedItemsPage.this)) //
                                    .collect(Collectors.toList());

                    // Logging them is not a bad idea if the whole method fails
                    PortfolioPlugin.log(errors);

                    Display.getDefault().asyncExec(() -> setResults(entries, errors));

                    return Status.OK_STATUS;
                }
            }.schedule();
        }
        catch (Exception e)
        {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void afterPage()
    {
        preferences.setValue(IMPORT_TARGET_ACCOUNT + extractor.getClass().getSimpleName(), getAccount().getUUID());
        preferences.setValue(IMPORT_TARGET_PORTFOLIO + extractor.getClass().getSimpleName(), getPortfolio().getUUID());
    }

    private void setResults(List<ExtractedEntry> entries, List<Exception> errors)
    {
        checkEntries(entries);

        allEntries.addAll(entries);
        tableViewer.setInput(allEntries);
        errorTableViewer.setInput(errors);

        for (ExtractedEntry entry : entries)
        {
            if (entry.getItem() instanceof Extractor.AccountTransferItem)
            {
                lblSecondaryAccount.setVisible(true);
                secondaryAccount.getControl().setVisible(true);
                addTargetAccountColumn();
            }
            else if (entry.getItem() instanceof Extractor.PortfolioTransferItem)
            {
                lblSecondaryPortfolio.setVisible(true);
                secondaryPortfolio.getControl().setVisible(true);
                addTargetPortfolioColumn();
            }
        }
    }

    private void checkEntriesAndRefresh(List<ExtractedEntry> entries)
    {
        checkEntries(entries);
        tableViewer.refresh();
    }

    private void checkEntries(List<ExtractedEntry> entries)
    {
        List<ImportAction> actions = new ArrayList<>();
        actions.add(new CheckValidTypesAction());
        actions.add(new DetectDuplicatesAction());
        actions.add(new CheckCurrenciesAction());

        for (ExtractedEntry entry : entries)
        {
            entry.clearStatus();
            for (ImportAction action : actions)
                entry.addStatus(entry.getItem().apply(action, entry));
        }
    }

    private boolean isAccountItem(Object entry)
    {
        if (entry instanceof ExtractedEntry)
        {
            Item item = ((ExtractedEntry) entry).getItem();
            Annotated subject = item.getSubject();
            if (subject instanceof BuySellEntry || subject instanceof Transaction
                            || subject instanceof AccountTransferEntry) { return true; }
        }
        return false;
    }
    
    private boolean isPortfolioItem(Object entry)
    {
        if (entry instanceof ExtractedEntry)
        {
            Item item = ((ExtractedEntry) entry).getItem();
            Annotated subject = item.getSubject();
            if (subject instanceof BuySellEntry || subject instanceof Transaction
                            || subject instanceof PortfolioTransferEntry) { return true; }
        }
        return false;
    }

    private boolean isAccountTransferItem(Object entry)
    {
        if (entry instanceof ExtractedEntry)
        {
            Item item = ((ExtractedEntry) entry).getItem();
            Annotated subject = item.getSubject();
            if (subject instanceof AccountTransferEntry) { return true; }
        }
        return false;
    }

    private boolean isPortfolioTransferItem(Object entry)
    {
        if (entry instanceof ExtractedEntry)
        {
            Item item = ((ExtractedEntry) entry).getItem();
            Annotated subject = item.getSubject();
            if (subject instanceof PortfolioTransferEntry) { return true; }
        }
        return false;
    }

    abstract static class FormattedLabelProvider extends StyledCellLabelProvider
    {
        private static Styler strikeoutStyler = new Styler()
        {
            @Override
            public void applyStyles(TextStyle textStyle)
            {
                textStyle.strikeout = true;
            }
        };

        public abstract String getText(ExtractedEntry element);
        
        @Override
        public String getToolTipText(Object element)
        {
            if (element instanceof ExtractedEntry)
            {
                return getText((ExtractedEntry) element);
            }
            return super.getToolTipText(element);
        }

        public Image getImage(ExtractedEntry element)
        {
            return null;
        }

        @Override
        public void update(ViewerCell cell)
        {
            ExtractedEntry entry = (ExtractedEntry) cell.getElement();
            String text = getText(entry);
            if (text == null)
                text = ""; //$NON-NLS-1$

            boolean strikeout = !entry.isImported();
            StyledString styledString = new StyledString(text, strikeout ? strikeoutStyler : null);

            cell.setText(styledString.toString());
            cell.setStyleRanges(styledString.getStyleRanges());
            cell.setImage(getImage(entry));

            super.update(cell);
        }
    }

    private abstract class NamedEditingSupport<T extends Named> extends EditingSupport
    {
        private List<T> named;

        public NamedEditingSupport(TableViewer viewer, List<T> named)
        {
            super(viewer);
            this.named = named;
        }

        @Override
        protected CellEditor getCellEditor(Object element)
        {
            String[] names = new String[named.size()];
            int i = 0;
            for (T namedElement : named)
            {
                names[i++] = namedElement.getName();
            }
            return new ComboBoxCellEditor(getViewer().getTable(), names);
        }

        @Override
        protected boolean canEdit(Object element)
        {
            return named.size() > 1 && isCorrectItem(element);
        }

        protected abstract boolean isCorrectItem(Object element);

        @Override
        protected Object getValue(Object element)
        {
            return named.indexOf(element);
        }

        @Override
        protected void setValue(Object element, Object value)
        {
            if (isCorrectItem(element))
            {
                ExtractedEntry entry = (ExtractedEntry) element;
                int index = (int) value;
                if (index >= 0 && named.size() >= index)
                {
                    T namedElement = named.get(index);
                    doSetValue(entry, namedElement);
                }
                getViewer().update(entry, null);
            }
        }

        protected abstract void doSetValue(ExtractedEntry entry, T namedElement);

        @Override
        public TableViewer getViewer()
        {
            return (TableViewer) super.getViewer();
        }
    }

    private class AccountEditingSupport extends NamedEditingSupport<Account>
    {
        public AccountEditingSupport(TableViewer viewer, List<Account> named)
        {
            super(viewer, named);
        }

        @Override
        protected boolean isCorrectItem(Object element)
        {
            return isAccountItem(element);
        }

        @Override
        protected void doSetValue(ExtractedEntry entry, Account account)
        {
            entry.setAccount(account);
        }
    }

    private class TargetAccountEditingSupport extends NamedEditingSupport<Account>
    {
        public TargetAccountEditingSupport(TableViewer viewer, List<Account> named)
        {
            super(viewer, named);
        }

        @Override
        protected boolean isCorrectItem(Object element)
        {
            return isAccountTransferItem(element);
        }

        @Override
        protected void doSetValue(ExtractedEntry entry, Account account)
        {
            entry.setSecondaryAccount(account);
        }
    }

    private class PortfolioEditingSupport extends NamedEditingSupport<Portfolio>
    {
        public PortfolioEditingSupport(TableViewer viewer, List<Portfolio> named)
        {
            super(viewer, named);
        }

        @Override
        protected boolean isCorrectItem(Object element)
        {
            return isPortfolioItem(element);
        }

        @Override
        protected void doSetValue(ExtractedEntry entry, Portfolio portfolio)
        {
            entry.setPortfolio(portfolio);
        }
    }

    private class TargetPortfolioEditingSupport extends NamedEditingSupport<Portfolio>
    {
        public TargetPortfolioEditingSupport(TableViewer viewer, List<Portfolio> named)
        {
            super(viewer, named);
        }

        @Override
        protected boolean isCorrectItem(Object element)
        {
            return isPortfolioTransferItem(element);
        }

        @Override
        protected void doSetValue(ExtractedEntry entry, Portfolio portfolio)
        {
            entry.setSecondaryPortfolio(portfolio);
        }
    }

    abstract class AssignToNamedAction<T extends Named> extends Action
    {
        protected final T named;

        public AssignToNamedAction(T named)
        {
            super(named.getName());
            this.named = named;
        }

        protected abstract void setValue(ExtractedEntry entry);

        @Override
        public void run()
        {
            for (Object element : ((IStructuredSelection) tableViewer.getSelection()).toList())
            {
                setValue((ExtractedEntry) element);
            }
            tableViewer.refresh();
        }
    }

    class AssignToAccountAction extends AssignToNamedAction<Account>
    {
        public AssignToAccountAction(Account account)
        {
            super(account);
        }

        @Override
        public void setValue(ExtractedEntry entry)
        {
            entry.setAccount(named);
        }
    }

    class AssignToPortfolioAction extends AssignToNamedAction<Portfolio>
    {
        public AssignToPortfolioAction(Portfolio portfolio)
        {
            super(portfolio);
        }

        @Override
        public void setValue(ExtractedEntry entry)
        {
            entry.setPortfolio(named);
        }
    }

    class AssignToTargetAccountAction extends AssignToNamedAction<Account>
    {
        public AssignToTargetAccountAction(Account account)
        {
            super(account);
        }

        @Override
        public void setValue(ExtractedEntry entry)
        {
            entry.setSecondaryAccount(named);
        }
    }

    class AssignToTargetPortfolioAction extends AssignToNamedAction<Portfolio>
    {
        public AssignToTargetPortfolioAction(Portfolio portfolio)
        {
            super(portfolio);
        }

        @Override
        public void setValue(ExtractedEntry entry)
        {
            entry.setSecondaryPortfolio(named);
        }
    }
}
