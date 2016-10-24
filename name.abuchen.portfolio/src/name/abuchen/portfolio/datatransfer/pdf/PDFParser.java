package name.abuchen.portfolio.datatransfer.pdf;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.Extractor.Item;

/* package */final class PDFParser
{
    /* package */static class DocumentType
    {
        private String marker;
        private List<Block> blocks = new ArrayList<>();
        private Map<String, String> context = new HashMap<>();
        private BiConsumer<Map<String, String>, String[]> contextProvider;

        public DocumentType(String marker)
        {
            this(marker, null);
        }

        public DocumentType(String marker, BiConsumer<Map<String, String>, String[]> contextProvider)
        {
            this.marker = marker;
            this.contextProvider = contextProvider;
        }

        public boolean matches(String text)
        {
            return text.contains(marker);
        }

        public void addBlock(Block block)
        {
            blocks.add(block);
        }

        public List<Block> getBlocks()
        {
            return blocks;
        }

        /**
         * Gets the current context for this parse run.
         * 
         * @return current context map
         */
        public Map<String, String> getCurrentContext()
        {
            return context;
        }

        public void parse(String filename, List<Item> items, String text)
        {
            String[] lines = text.split("\\r?\\n"); //$NON-NLS-1$

            // reset context and parse it from this file
            context.clear();
            parseContext(context, filename, lines);

            for (Block block : blocks)
                block.parse(filename, items, lines);
        }

        /**
         * Parses the current context and could be overridden in a subclass to
         * fill the context.
         * 
         * @param context
         *            context map
         * @param filename
         *            current filename
         * @param lines
         *            content lines of the file
         */
        protected void parseContext(Map<String, String> context, String filename, String[] lines)
        {
            // if a context provider is given call it, else parse the current
            // context in a subclass
            if(contextProvider!=null)
            {
              contextProvider.accept(context, lines);  
            }
        }
    }

    /* package */static class Block
    {
        private Pattern marker;
        private Transaction<?> transaction;

        public Block(String marker)
        {
            this.marker = Pattern.compile(marker);
        }

        public void set(Transaction<?> transaction)
        {
            this.transaction = transaction;
        }

        public void parse(String filename, List<Item> items, String[] lines)
        {
            int currentLastTransactionLine = lines.length-1;
            for (int ii = lines.length-1; ii >= 0; ii--)
            {
                Matcher matcher = marker.matcher(lines[ii]);
                if (matcher.matches())
                {
                    transaction.parse(filename, items, lines, ii, currentLastTransactionLine);
                    currentLastTransactionLine = ii-1;
                }
            }
        }
    }

    /* package */static class Transaction<T>
    {
        private Supplier<T> supplier;
        private Function<T, Item> wrapper;
        private List<Section<T>> sections = new ArrayList<>();

        public Transaction<T> subject(Supplier<T> supplier)
        {
            this.supplier = supplier;
            return this;
        }

        public Section<T> section(String... attributes)
        {
            Section<T> section = new Section<>(this, attributes);
            sections.add(section);
            return section;
        }

        public Transaction<T> wrap(Function<T, Item> wrapper)
        {
            this.wrapper = wrapper;
            return this;
        }

        public void parse(String filename, List<Item> items, String[] lines, int lineNoStart, int lineNoEnd)
        {
            T target = supplier.get();

            for (Section<T> section : sections)
                section.parse(filename, items, lines, lineNoStart, lineNoEnd, target);

            if (wrapper == null)
                throw new IllegalArgumentException("Wrapping function missing"); //$NON-NLS-1$

            Item item = wrapper.apply(target);
            if (item != null)
                items.add(item);
        }
    }

    /* package */static class Section<T>
    {
        private boolean isOptional = false;
        private Transaction<T> transaction;
        private String[] attributes;
        private List<Pattern> pattern = new ArrayList<>();
        private BiConsumer<T, Map<String, String>> assignment;

        public Section(Transaction<T> transaction, String[] attributes)
        {
            this.transaction = transaction;
            this.attributes = attributes;
        }

        public Section<T> optional()
        {
            this.isOptional = true;
            return this;
        }

        public Section<T> find(String string)
        {
            pattern.add(Pattern.compile("^" + string + "$")); //$NON-NLS-1$ //$NON-NLS-2$
            return this;
        }

        public Section<T> match(String regex)
        {
            pattern.add(Pattern.compile(regex));
            return this;
        }

        public Transaction<T> assign(BiConsumer<T, Map<String, String>> assignment)
        {
            this.assignment = assignment;
            return transaction;
        }

        public void parse(String filename, List<Item> items, String[] lines, int lineNo, int lineNoEnd, T target)
        {
            Map<String, String> values = new HashMap<>();

            int patternNo = 0;
            for (int ii = lineNo; ii <= lineNoEnd; ii++)
            {
                Pattern p = pattern.get(patternNo);
                Matcher m = p.matcher(lines[ii]);
                if (m.matches())
                {
                    // extract attributes
                    extractAttributes(values, p, m);

                    // next pattern?
                    patternNo++;
                    if (patternNo >= pattern.size())
                        break;
                }
            }

            if (patternNo < pattern.size())
            {
                // if section is optional, ignore if patterns do not match
                if (isOptional)
                    return;

                throw new IllegalArgumentException(MessageFormat.format(Messages.MsgErrorNotAllPatternMatched,
                                patternNo, pattern.size(), pattern.toString(), filename));
            }

            if (values.size() != attributes.length)
                throw new IllegalArgumentException(MessageFormat.format(Messages.MsgErrorMissingValueMatches,
                                values.keySet().toString(), Arrays.toString(attributes), filename));

            if (assignment == null)
                throw new IllegalArgumentException("Assignment function missing"); //$NON-NLS-1$

            assignment.accept(target, values);
        }

        private void extractAttributes(Map<String, String> values, Pattern p, Matcher m)
        {
            for (String attribute : attributes)
            {
                if (p.pattern().contains("<" + attribute + ">")) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    String v = m.group(attribute);
                    if (v != null)
                        values.put(attribute, v);
                }
            }
        }
    }

    private PDFParser()
    {}
}
