package name.abuchen.portfolio.ui.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.snapshot.filter.ClientFilter;
import name.abuchen.portfolio.snapshot.filter.PortfolioClientFilter;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.dialogs.ListSelectionDialog;

public final class ClientFilterMenu implements IMenuListener
{
    private static class Item
    {
        String label;
        String uuids;
        ClientFilter filter;

        public Item(String label, String uuids, ClientFilter filter)
        {
            this.label = label;
            this.uuids = uuids;
            this.filter = filter;
        }
    }

    private static final int MAXIMUM_NO_CUSTOM_ITEMS = 5;

    private final Client client;
    private final IPreferenceStore preferences;
    private final List<Consumer<ClientFilter>> listeners = new ArrayList<>();

    private List<Item> defaultItems = new ArrayList<>();
    private LinkedList<Item> customItems = new LinkedList<>();
    private Item selectedItem;

    public ClientFilterMenu(Client client, IPreferenceStore preferences, Consumer<ClientFilter> listener)
    {
        this.client = client;
        this.preferences = preferences;
        this.listeners.add(listener);

        selectedItem = new Item(Messages.PerformanceChartLabelEntirePortfolio, null, c -> c);
        defaultItems.add(selectedItem);

        client.getPortfolios().forEach(portfolio -> {
            defaultItems.add(new Item(portfolio.getName(), null, new PortfolioClientFilter(portfolio)));
            defaultItems.add(new Item(portfolio.getName() + " + " + portfolio.getReferenceAccount().getName(), //$NON-NLS-1$
                            null, new PortfolioClientFilter(portfolio, portfolio.getReferenceAccount())));
        });

        loadCustomItems();
    }

    private void loadCustomItems()
    {
        String code = preferences.getString(ClientFilterDropDown.class.getSimpleName());
        if (code == null || code.isEmpty())
            return;

        Map<String, Object> uuid2object = new HashMap<>();
        client.getPortfolios().forEach(p -> uuid2object.put(p.getUUID(), p));
        client.getAccounts().forEach(a -> uuid2object.put(a.getUUID(), a));

        String[] items = code.split(";"); //$NON-NLS-1$
        for (String item : items)
        {
            String[] uuids = item.split(","); //$NON-NLS-1$
            Object[] objects = Arrays.stream(uuids).map(uuid2object::get).filter(o -> o != null).toArray();

            if (objects.length > 0)
            {
                Item newItem = buildItem(objects);
                customItems.add(newItem);
            }
        }
    }

    @Override
    public void menuAboutToShow(IMenuManager manager)
    {
        defaultItems.forEach(item -> {
            Action action = new SimpleAction(item.label, a -> {
                selectedItem = item;
                listeners.forEach(l -> l.accept(item.filter));
            });
            action.setChecked(item.equals(selectedItem));
            manager.add(action);
        });

        manager.add(new Separator());
        customItems.forEach(item -> {
            Action action = new SimpleAction(item.label, a -> {
                selectedItem = item;

                customItems.remove(item);
                customItems.addFirst(item);

                listeners.forEach(l -> l.accept(item.filter));
            });
            action.setChecked(item.equals(selectedItem));
            manager.add(action);
        });

        manager.add(new Separator());
        manager.add(new SimpleAction(Messages.LabelClientFilterNew, a -> createCustomFilter()));
        manager.add(new SimpleAction(Messages.LabelClientClearCustomItems, a -> {
            if (customItems.contains(selectedItem))
            {
                selectedItem = defaultItems.get(0);
                listeners.forEach(l -> l.accept(selectedItem.filter));
            }

            customItems.clear();
            preferences.setToDefault(ClientFilterDropDown.class.getSimpleName());
        }));
    }

    private void createCustomFilter()
    {
        LabelProvider labelProvider = new LabelProvider()
        {
            @Override
            public Image getImage(Object element)
            {
                return element instanceof Account ? Images.ACCOUNT.image() : Images.PORTFOLIO.image();
            }
        };
        ListSelectionDialog dialog = new ListSelectionDialog(Display.getDefault().getActiveShell(), labelProvider);

        dialog.setTitle(Messages.LabelClientFilterDialogTitle);
        dialog.setMessage(Messages.LabelClientFilterDialogMessage);

        List<Object> elements = new ArrayList<>();
        elements.addAll(client.getPortfolios());
        elements.addAll(client.getAccounts());
        dialog.setElements(elements);

        if (dialog.open() == ListSelectionDialog.OK)
        {
            Object[] selected = dialog.getResult();
            if (selected.length > 0)
            {
                Item newItem = buildItem(selected);

                selectedItem = newItem;
                customItems.addFirst(newItem);

                if (customItems.size() > MAXIMUM_NO_CUSTOM_ITEMS)
                    customItems.removeLast();

                preferences.putValue(ClientFilterDropDown.class.getSimpleName(), String.join(";", //$NON-NLS-1$
                                customItems.stream().map(i -> i.uuids).collect(Collectors.toList())));

                listeners.forEach(l -> l.accept(newItem.filter));
            }
        }
    }

    private Item buildItem(Object[] selected)
    {
        List<Portfolio> portfolios = Arrays.stream(selected).filter(o -> o instanceof Portfolio).map(o -> (Portfolio) o)
                        .collect(Collectors.toList());
        List<Account> accounts = Arrays.stream(selected).filter(o -> o instanceof Account).map(o -> (Account) o)
                        .collect(Collectors.toList());

        String label = String.join(", ", //$NON-NLS-1$
                        Arrays.stream(selected).map(String::valueOf).collect(Collectors.toList()));

        String uuids = String.join(",", //$NON-NLS-1$
                        Arrays.stream(selected).map(
                                        o -> o instanceof Account ? ((Account) o).getUUID() : ((Portfolio) o).getUUID())
                                        .collect(Collectors.toList()));

        return new Item(label, uuids, new PortfolioClientFilter(portfolios, accounts));
    }

    public boolean hasActiveFilter()
    {
        return defaultItems.indexOf(selectedItem) != 0;
    }

    public ClientFilter getSelectedFilter()
    {
        return selectedItem.filter;
    }

    public void addListener(Consumer<ClientFilter> listener)
    {
        listeners.add(listener);
    }
}
