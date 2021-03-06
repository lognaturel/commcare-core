package org.javarosa.core.services.locale;

import org.javarosa.core.util.NoLocalizedTextException;
import org.javarosa.core.util.UnregisteredLocaleException;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapListPoly;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

/**
 * The Localizer object maintains mappings for locale ID's and Object
 * ID's to the String values associated with them in different
 * locales.
 *
 * @author Drew Roos/Clayton Sims
 */
public class Localizer implements Externalizable {
    private Vector<String> locales;
    private Hashtable<String, Vector<LocaleDataSource>> localeResources;
    private Hashtable<String, String> currentLocaleData;
    private String defaultLocale;
    private String currentLocale;
    private boolean fallbackDefaultLocale;
    private boolean fallbackDefaultForm;

    /**
     * Default constructor. Disables all fallback modes.
     */
    public Localizer() {
        this(false, false);
    }

    /**
     * @param fallbackDefaultLocale If true, search the default locale when no translation for a particular text handle
     *                              is found in the current locale.
     * @param fallbackDefaultForm   If true, search the default text form when no translation is available for the
     *                              specified text form ('long', 'short', etc.). Note: form is specified by appending ';[form]' onto the text ID.
     */
    public Localizer(boolean fallbackDefaultLocale, boolean fallbackDefaultForm) {
        localeResources = new Hashtable<>();
        currentLocaleData = new Hashtable<>();
        locales = new Vector<>();
        defaultLocale = null;
        currentLocale = null;
        this.fallbackDefaultLocale = fallbackDefaultLocale;
        this.fallbackDefaultForm = fallbackDefaultForm;
    }

    /**
     * Create a new locale (with no mappings). Do nothing if the locale is already defined.
     *
     * @param locale Locale to add. Must not be null.
     * @return True if the locale was not already defined.
     * @throws NullPointerException if locale is null
     */
    public boolean addAvailableLocale(String locale) {
        if (hasLocale(locale)) {
            return false;
        } else {
            locales.addElement(locale);
            localeResources.put(locale, new Vector<LocaleDataSource>());
            return true;
        }
    }

    /**
     * Get a list of defined locales.
     *
     * @return Array of defined locales, in order they were created.
     */
    public String[] getAvailableLocales() {
        String[] data = new String[locales.size()];
        locales.copyInto(data);
        return data;
    }

    /**
     * Get whether a locale is defined. The locale need not have any mappings.
     *
     * @param locale Locale
     * @return Whether the locale is defined. False if null
     */
    public boolean hasLocale(String locale) {
        return locale != null && locales.contains(locale);
    }

    /**
     * Get the current locale.
     *
     * @return Current locale.
     */
    public String getLocale() {
        return currentLocale;
    }

    /**
     * Set the current locale. The locale must be defined. Will notify all registered ILocalizables of the change in locale.
     *
     * @param currentLocale Locale. Must be defined and not null.
     * @throws UnregisteredLocaleException If locale is null or not defined.
     */
    public void setLocale(String currentLocale) {
        if (!hasLocale(currentLocale)) {
            throw new UnregisteredLocaleException("Attempted to set to a locale that is not defined. Attempted Locale: " + currentLocale);
        }

        if (!currentLocale.equals(this.currentLocale)) {
            this.currentLocale = currentLocale;
        }
        loadCurrentLocaleResources();
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * Set the default locale. The locale must be defined.
     *
     * @param defaultLocale Default locale. Must be defined. May be null, in which case there will be no default locale.
     * @throws UnregisteredLocaleException If locale is not defined.
     */
    public void setDefaultLocale(String defaultLocale) {
        if (defaultLocale != null && !hasLocale(defaultLocale)) {
            throw new UnregisteredLocaleException("Attempted to set default to a locale that is not defined");
        }

        this.defaultLocale = defaultLocale;
    }

    /**
     * Set the current locale to the default locale. The default locale must be set.
     *
     * @throws IllegalStateException If default locale is not set.
     */
    public void setToDefault() {
        if (defaultLocale == null) {
            throw new IllegalStateException("Attempted to set to default locale when default locale not set");
        }

        setLocale(defaultLocale);
    }

    /**
     * Constructs a body of local resources to be the set of Current Locale Data.
     *
     * After loading, the current locale data will contain definitions for each
     * entry defined by the current locale resources, as well as definitions for any
     * entry present in the fallback resources but not in those of the current locale.
     *
     * The procedure to accomplish this set is as follows, with overwritting occuring
     * when a collision occurs:
     *
     * 1. Load all of the in memory definitions for the default locale if fallback is enabled
     * 2. For each resource file for the default locale, load each definition if fallback is enabled
     * 3. Load all of the in memory definitions for the current locale
     * 4. For each resource file for the current locale, load each definition
     */
    private void loadCurrentLocaleResources() {
        currentLocaleData = getLocaleData(currentLocale);
    }

    /**
     * Registers a resource file as a source of locale data for the specified
     * locale.
     *
     * @param locale   The locale of the definitions provided.
     * @param resource A LocaleDataSource containing string data for the locale provided
     * @throws NullPointerException if resource or locale are null
     */
    public void registerLocaleResource(String locale, LocaleDataSource resource) {
        if (locale == null) {
            throw new NullPointerException("Attempt to register a data source to a null locale in the localizer");
        }
        if (resource == null) {
            throw new NullPointerException("Attempt to register a null data source in the localizer");
        }
        if (localeResources.containsKey(locale)) {
            Vector<LocaleDataSource> resources = localeResources.get(locale);
            resources.addElement(resource);
        } else {
            Vector<LocaleDataSource> resources = new Vector<>();
            resources.addElement(resource);
            localeResources.put(locale, resources);
        }

        if (locale.equals(currentLocale) || locale.equals(defaultLocale)) {
            // Reload locale data if the resource is for a locale in use
            loadCurrentLocaleResources();
        }
    }

    /**
     * Get the set of mappings for a locale.
     *
     * @return Hashtable representing text mappings for this locale. Returns null if locale not defined or null.
     */
    public Hashtable<String, String> getLocaleData(String locale) {
        if (locale == null || !this.locales.contains(locale)) {
            return null;
        }

        //It's very important that any default locale contain the appropriate strings to localize the interface
        //for any possible language. As such, we'll keep around a table with only the default locale keys to
        //ensure that there are no localizations which are only present in another locale, which causes ugly
        //and difficult to trace errors.
        HashSet<String> defaultLocaleKeys = new HashSet<>();

        //This table will be loaded with the default values first (when applicable), and then with any
        //language specific translations overwriting the existing values.
        Hashtable<String, String> data = new Hashtable<>();

        // If there's a default locale, we load all of its elements into memory first, then allow
        // the current locale to overwrite any differences between the two.    
        if (fallbackDefaultLocale && defaultLocale != null) {
            for (LocaleDataSource defaultResource : localeResources.get(defaultLocale)) {
                data.putAll(defaultResource.getLocalizedText());
            }
            defaultLocaleKeys.addAll(data.keySet());
        }

        for (LocaleDataSource resource : localeResources.get(locale)) {
            data.putAll(resource.getLocalizedText());
        }

        //If we're using a default locale, now we want to make sure that it has all of the keys
        //that the locale we want to use does. Otherwise, the app will crash when we switch to 
        //a locale that doesn't contain the key.
        if (fallbackDefaultLocale && defaultLocale != null) {
            String missingKeys = "";
            int keysmissing = 0;
            for (Enumeration en = data.keys(); en.hasMoreElements(); ) {
                String key = (String)en.nextElement();
                if (!defaultLocaleKeys.contains(key)) {
                    missingKeys += key + ",";
                    keysmissing++;
                }
            }
            if (keysmissing > 0) {
                //Is there a good way to localize these exceptions?
                throw new NoLocalizedTextException("Error loading locale " + locale +
                        ". There were " + keysmissing + " keys which were contained in this locale, but were not " +
                        "properly registered in the default Locale. Any keys which are added to a locale should always " +
                        "be added to the default locale to ensure appropriate functioning.\n" +
                        "The missing translations were for the keys: " + missingKeys, missingKeys, defaultLocale);
            }
        }

        return data;
    }

    /**
     * Get the mappings for a locale, but throw an exception if locale is not defined.
     *
     * @param locale Locale
     * @return Text mappings for locale.
     * @throws UnregisteredLocaleException If locale is not defined or null.
     */
    public Hashtable<String, String> getLocaleMap(String locale) {
        Hashtable<String, String> mapping = getLocaleData(locale);
        if (mapping == null) {
            throw new UnregisteredLocaleException("Attempted to access an undefined locale.");
        }
        return mapping;
    }

    /**
     * Determine whether a locale has a mapping for a given text handle. Only tests the specified locale and form; does
     * not fallback to any default locale or text form.
     *
     * @param locale Locale. Must be defined and not null.
     * @param textID Text handle.
     * @return True if a mapping exists for the text handle in the given locale.
     * @throws UnregisteredLocaleException If locale is not defined.
     */
    public boolean hasMapping(String locale, String textID) {
        if (locale == null || !locales.contains(locale)) {
            throw new UnregisteredLocaleException("Attempted to access an undefined locale (" + locale + ") while checking for a mapping for  " + textID);
        }
        Vector<LocaleDataSource> resources = localeResources.get(locale);
        for (LocaleDataSource source : resources) {
            if (source.getLocalizedText().containsKey(textID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     */
    public String getText(String textID) {
        return getText(textID, currentLocale);
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param args   arguments for string variables.
     * @return Localized text
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     * @throws NoLocalizedTextException    If there is no text for the specified id
     */
    public String getText(String textID, String[] args) {
        String text = getText(textID, currentLocale);
        if (text != null) {
            text = processArguments(text, args);
        } else {
            throw new NoLocalizedTextException("The Localizer could not find a definition for ID: " + textID + " in the '" + currentLocale + "' locale.", textID, currentLocale);
        }
        return text;
    }

    /**
     * Retrieve the localized text for a text handle in the current locale. See getText(String, String) for details.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param args   arguments for string variables.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If current locale is not set.
     * @throws NullPointerException        if textID is null
     * @throws NoLocalizedTextException    If there is no text for the specified id
     */
    public String getText(String textID, Hashtable args) {
        String text = getText(textID, currentLocale);
        if (text != null) {
            text = processArguments(text, args);
        } else {
            throw new NoLocalizedTextException("The Localizer could not find a definition for ID: " + textID + " in the '" + currentLocale + "' locale.", textID, currentLocale);
        }
        return text;
    }

    /**
     * Retrieve the localized text for a text handle in the given locale. If no mapping is found initially, then,
     * depending on enabled fallback modes, other places will be searched until a mapping is found.
     * <p>
     * The search order is thus:
     * 1) Specified locale, specified text form
     * 2) Specified locale, default text form
     * 3) Default locale, specified text form
     * 4) Default locale, default text form
     * <p>
     * (1) and (3) are only searched if a text form ('long', 'short', etc.) is specified.
     * If a text form is specified, (2) and (4) are only searched if default-form-fallback mode is enabled.
     * (3) and (4) are only searched if default-locale-fallback mode is enabled. It is not an error in this situation
     * if no default locale is set; (3) and (4) will simply not be searched.
     *
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @param locale Locale. Must be defined and not null.
     * @return Localized text. If no text is found after using all fallbacks, return null.
     * @throws UnregisteredLocaleException If the locale is not defined or null.
     * @throws NullPointerException        if textID is null
     */
    public String getText(String textID, String locale) {
        String text = getRawText(locale, textID);
        if (text == null && fallbackDefaultForm && textID.contains(";")) {
            text = getRawText(locale, textID.substring(0, textID.indexOf(";")));
        }
        //Update: We handle default text without forms without needing to do this. We still need it for default text with default forms, though  
        if (text == null && fallbackDefaultLocale && !locale.equals(defaultLocale) && defaultLocale != null && fallbackDefaultForm) {
            text = getText(textID, defaultLocale);
        }
        return text;
    }

    /**
     * Get text for locale and exact text ID only, not using any fallbacks.
     *
     * NOTE: This call will only return the full compliment of available strings if and
     * only if the requested locale is current. Otherwise it will only retrieve strings
     * declared at runtime.
     *
     * @param locale Locale. Must be defined and not null.
     * @param textID Text handle (text ID appended with optional text form). Must not be null.
     * @return Localized text. Return null if none found.
     * @throws UnregisteredLocaleException If the locale is not defined or null.
     * @throws NullPointerException        if textID is null
     */
    public String getRawText(String locale, String textID) {
        if (locale == null) {
            throw new UnregisteredLocaleException("Null locale when attempting to fetch text id: " + textID);
        }
        if (locale.equals(currentLocale)) {
            return currentLocaleData.get(textID);
        } else {
            return getLocaleMap(locale).get(textID);
        }
    }

    public static Vector getArgs(String text) {
        Vector<String> args = new Vector<>();
        int i = text.indexOf("${");
        while (i != -1) {
            int j = text.indexOf("}", i);
            if (j == -1) {
                System.err.println("Warning: unterminated ${...} arg");
                break;
            }

            String arg = text.substring(i + 2, j);
            if (!args.contains(arg)) {
                args.addElement(arg);
            }

            i = text.indexOf("${", j + 1);
        }
        return args;
    }

    /**
     * Replace all arguments in 'text', of form ${x}, with the value 'x' maps
     * to in 'args'.
     *
     * @param text String that potentially contains arguments to be evaluated
     *             in the 'args' context.
     * @param args Hashtable from string keys to values that can be cast to
     *             String.
     * @return String with all arguments from 'text' present in 'args' context
     * replaced with their values.
     */
    public static String processArguments(String text, Hashtable args) {
        int i = text.indexOf("${");

        // find every instance of ${some_key} in text and replace it with the
        // value that some_key maps to in args.
        while (i != -1) {
            int j = text.indexOf("}", i);

            // abort if no closing bracket
            if (j == -1) {
                System.err.println("Warning: unterminated ${...} arg");
                break;
            }

            String argName = text.substring(i + 2, j);
            String argVal = (String)args.get(argName);
            // if we found a mapping in the args table, perform text substitution
            if (argVal != null) {
                text = text.substring(0, i) + argVal + text.substring(j + 1);
                j = i + argVal.length() - 1;
            }

            i = text.indexOf("${", j + 1);
        }
        return text;
    }

    public static String processArguments(String text, String[] args) {
        return processArguments(text, args, 0);
    }

    public static String processArguments(String text, String[] args, int currentArg) {
        if (text.contains("${") && args.length > currentArg) {
            int index = extractNextIndex(text, args);

            if (index == -1) {
                index = currentArg;
                currentArg++;
            }

            String value = args[index];
            String[] replaced = replaceFirstValue(text, value);
            return replaced[0] + processArguments(replaced[1], args, currentArg);
        } else {
            return text;
        }
    }

    public static String clearArguments(String text) {
        Vector v = getArgs(text);
        String[] empty = new String[v.size()];
        for (int i = 0; i < empty.length; ++i) {
            empty[i] = "";
        }
        return processArguments(text, empty);
    }

    private static int extractNextIndex(String text, String[] args) {
        int start = text.indexOf("${");
        int end = text.indexOf("}", start);

        if (start != -1 && end != -1) {
            String val = text.substring(start + "${".length(), end);
            try {
                int index = Integer.parseInt(val);
                if (index >= 0 && index < args.length) {
                    return index;
                }
            } catch (NumberFormatException nfe) {
                return -1;
            }
        }

        return -1;
    }

    private static String[] replaceFirstValue(String text, String value) {
        int start = text.indexOf("${");
        int end = text.indexOf("}", start);

        return new String[]{text.substring(0, start) + value, text.substring(end + 1, text.length())};
    }

    @Override
    public void readExternal(DataInputStream dis, PrototypeFactory pf) throws IOException, DeserializationException {
        fallbackDefaultLocale = ExtUtil.readBool(dis);
        fallbackDefaultForm = ExtUtil.readBool(dis);
        localeResources = (Hashtable)ExtUtil.read(dis, new ExtWrapMap(String.class, new ExtWrapListPoly()), pf);
        locales = (Vector)ExtUtil.read(dis, new ExtWrapList(String.class), pf);
        setDefaultLocale((String)ExtUtil.read(dis, new ExtWrapNullable(String.class), pf));
        String currentLocale = (String)ExtUtil.read(dis, new ExtWrapNullable(String.class), pf);
        if (currentLocale != null) {
            setLocale(currentLocale);
        }
    }

    @Override
    public void writeExternal(DataOutputStream dos) throws IOException {
        ExtUtil.writeBool(dos, fallbackDefaultLocale);
        ExtUtil.writeBool(dos, fallbackDefaultForm);
        ExtUtil.write(dos, new ExtWrapMap(localeResources, new ExtWrapListPoly()));
        ExtUtil.write(dos, new ExtWrapList(locales));
        ExtUtil.write(dos, new ExtWrapNullable(defaultLocale));
        ExtUtil.write(dos, new ExtWrapNullable(currentLocale));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Localizer) {
            Localizer l = (Localizer)o;

            //TODO: Compare all resources
            return (ExtUtil.equals(locales, l.locales, false) &&
                    ExtUtil.equals(localeResources, l.localeResources, true) &&
                    ExtUtil.equals(defaultLocale, l.defaultLocale, false) &&
                    ExtUtil.equals(currentLocale, l.currentLocale, true) &&
                    fallbackDefaultLocale == l.fallbackDefaultLocale &&
                    fallbackDefaultForm == l.fallbackDefaultForm);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = locales.hashCode() ^
                localeResources.hashCode() ^
                (fallbackDefaultLocale ? 0 : 31) ^
                (fallbackDefaultForm ? 0 : 31);
        if (defaultLocale != null) {
            hash ^= defaultLocale.hashCode();
        }
        if (currentLocale != null) {
            hash ^= currentLocale.hashCode();
        }
        return hash;
    }

    /**
     * For Testing: Get default locale fallback mode
     *
     * @return default locale fallback mode
     */
    public boolean getFallbackLocale() {
        return fallbackDefaultLocale;
    }

    /**
     * For Testing: Get default form fallback mode
     *
     * @return default form fallback mode
     */
    public boolean getFallbackForm() {
        return fallbackDefaultForm;
    }
}
