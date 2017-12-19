package name.abuchen.portfolio.ui.addons;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.commands.MCommandsFactory;
import org.eclipse.e4.ui.model.application.commands.MKeyBinding;
import org.eclipse.e4.ui.model.application.commands.MParameter;
import org.eclipse.e4.ui.workbench.IWorkbench;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.graphics.Image;
import org.osgi.service.event.Event;

import name.abuchen.portfolio.datatransfer.FileExtractorService;
import name.abuchen.portfolio.money.ExchangeRateProvider;
import name.abuchen.portfolio.money.ExchangeRateProviderFactory;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.log.LogEntryCache;
import name.abuchen.portfolio.ui.update.UpdateHelper;
import name.abuchen.portfolio.ui.util.ProgressMonitorFactory;
import name.abuchen.portfolio.ui.util.RecentFilesCache;

@SuppressWarnings("restriction")
public class StartupAddon
{
    private static final class UpdateExchangeRatesJob extends Job
    {
        private final IEventBroker broker;
        private final ExchangeRateProviderFactory factory;
        private final ExchangeRateProvider provider;

        private boolean loadDone = false;

        private UpdateExchangeRatesJob(IEventBroker broker, ExchangeRateProviderFactory factory,
                        ExchangeRateProvider provider)
        {
            super(MessageFormat.format(Messages.MsgUpdatingExchangeRates, provider.getName()));
            this.broker = broker;
            this.factory = factory;
            this.provider = provider;
        }

        @Override
        protected IStatus run(IProgressMonitor monitor)
        {
            if (!loadDone)
            {
                // load data from file only the first time around

                loadFromFile(monitor);
                loadDone = true;
            }

            updateOnline(monitor);

            schedule(1000L * 60 * 60 * 12); // every 12 hours

            return Status.OK_STATUS;
        }

        private void loadFromFile(IProgressMonitor monitor)
        {
            try
            {
                provider.load(monitor);
            }
            catch (Exception e)
            {
                // also catch runtime exceptions to make sure the update
                // method runs in any case
                PortfolioPlugin.log(e);
            }
            finally
            {
                factory.clearCache();
                broker.post(UIConstants.Event.ExchangeRates.LOADED, provider);
            }
        }

        private void updateOnline(IProgressMonitor monitor)
        {
            try
            {
                provider.update(monitor);
            }
            catch (IOException e)
            {
                PortfolioPlugin.log(e);
            }
            finally
            {
                factory.clearCache();
                broker.post(UIConstants.Event.ExchangeRates.LOADED, provider);
            }
        }
    }

    @PostConstruct
    public void setupProgressMontior(ProgressMonitorFactory factory)
    {
        IJobManager manager = Job.getJobManager();
        manager.setProgressProvider(factory);
    }

    @PostConstruct
    public void setupLogEntryCache(LogEntryCache cache)
    {
        // force creation of log entry cache
    }

    @PostConstruct
    public void setupRecentFilesCache(RecentFilesCache cache)
    {
        // force creation of recent files cache
    }

    @Inject
    @Optional
    public void checkForUpdates(@UIEventTopic(UIEvents.UILifeCycle.APP_STARTUP_COMPLETE) Event event, // NOSONAR
                    final IWorkbench workbench, final EPartService partService,
                    @Preference(value = UIConstants.Preferences.AUTO_UPDATE) boolean autoUpdate)
    {
        if (autoUpdate)
        {
            Job job = new Job(Messages.JobMsgCheckingForUpdates)
            {

                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                    try
                    {
                        monitor.beginTask(Messages.JobMsgCheckingForUpdates, 200);
                        UpdateHelper updateHelper = new UpdateHelper(workbench, partService);
                        updateHelper.runUpdate(monitor, true);
                    }
                    catch (CoreException e) // NOSONAR
                    {
                        PortfolioPlugin.log(e.getStatus());
                    }
                    return Status.OK_STATUS;
                }

            };
            job.setSystem(true);
            job.schedule(500);
        }
    }

    @PostConstruct
    public void updateExchangeRates(IEventBroker broker, ExchangeRateProviderFactory factory)
    {
        for (final ExchangeRateProvider provider : factory.getProviders())
        {
            Job job = new UpdateExchangeRatesJob(broker, factory, provider);
            job.schedule();
        }
    }

    @PreDestroy
    public void storeExchangeRates(ExchangeRateProviderFactory factory)
    {
        for (ExchangeRateProvider provider : factory.getProviders())
        {
            try
            {
                provider.save(new NullProgressMonitor());
            }
            catch (IOException e)
            {
                PortfolioPlugin.log(e);
            }
        }
    }

    @PostConstruct
    public void setMultipleWindowImages()
    {
        // setting window images
        // http://www.eclipse.org/forums/index.php/t/440442/

        Window.setDefaultImages(new Image[] { Images.LOGO_512.image(), Images.LOGO_256.image(), Images.LOGO_128.image(),
                        Images.LOGO_48.image(), Images.LOGO_32.image(), Images.LOGO_16.image() });
    }

    @PostConstruct
    public void replaceDefaultDialogImages()
    {
        ImageRegistry registry = JFaceResources.getImageRegistry();
        registry.put(Dialog.DLG_IMG_MESSAGE_ERROR, Images.ERROR.descriptor());
        registry.put(Dialog.DLG_IMG_MESSAGE_WARNING, Images.WARNING.descriptor());
        registry.put(Dialog.DLG_IMG_MESSAGE_INFO, Images.INFO.descriptor());
    }
    
    
    @PostConstruct
    public void initExtractorKeyBindings(MApplication application, FileExtractorService fileExtractorService)
    {
        // key bindings must be created programmatically created at startup, because the menu items are created dynamically
        fileExtractorService.getAll().forEach((type, extractorMap) -> {
            java.util.Optional<MCommand> commandOptional = application.getCommands().stream()
                            .filter(c -> c.getElementId().equals("name.abuchen.portfolio.ui.command.import.file"))
                            .findAny();
            if (commandOptional.isPresent())
            {
                MCommand command = commandOptional.get();
                
                java.util.Optional<MKeyBinding> matchingKeyBinding = application.getBindingTables().stream().map(bt -> {
                    return bt.getBindings().stream().filter(
                                    b -> ("name.abuchen.portfolio.ui.menu.extractor-type-" + type).equals(b.getElementId()))
                                    .collect(Collectors.toList());
                }).flatMap(Collection::stream).findFirst();
                if (!matchingKeyBinding.isPresent())
                {
                    MKeyBinding keyBinding = MCommandsFactory.INSTANCE.createKeyBinding();
                    keyBinding.setElementId("name.abuchen.portfolio.ui.menu.extractor-type-" + type);
                    keyBinding.setKeySequence("M1+I " + type.substring(0, 1).toUpperCase());
    
                    MParameter keyBindingParameter = MCommandsFactory.INSTANCE.createParameter();
                    keyBindingParameter.setContributorURI("platform:/plugin/name.abuchen.portfolio.bootstrap");
                    keyBindingParameter.setElementId("name.abuchen.portfolio.ui.menu.param.extractor-type-" + type);
                    keyBindingParameter.setName("name.abuchen.portfolio.ui.param.extractor-type");
                    keyBindingParameter.setValue(type);
    
                    keyBinding.getParameters().add(keyBindingParameter);
                    keyBinding.setCommand(command);
    
                    application.getBindingTables().stream().findFirst().get().getBindings().add(keyBinding);
                }
            }
        });
    }
    
}
