package org.javarosa.xpath.expr;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.xpath.parser.XPathSyntaxException;

import java.util.Hashtable;

public class XPathTranslateFunc extends XPathFuncExpr {
    public static final String NAME = "translate";
    private static final int EXPECTED_ARG_COUNT = 3;

    public XPathTranslateFunc() {
        name = NAME;
        expectedArgCount = EXPECTED_ARG_COUNT;
    }

    public XPathTranslateFunc(XPathExpression[] args) throws XPathSyntaxException {
        super(NAME, args, EXPECTED_ARG_COUNT, true);
    }

    @Override
    public Object evalBody(DataInstance model, EvaluationContext evalContext) {
        return translate(evaluatedArgs[0], evaluatedArgs[1], evaluatedArgs[2]);
    }

    /**
     * Replace each of a given set of characters with another set of characters.
     * If the characters to replace are "abc" and the replacement string is "def",
     * each "a" in the source string will be replaced with "d", each "b" with "e", etc.
     * If a character appears multiple times in the string of characters to replace, the
     * first occurrence is the one that will be used.
     *
     * Any extra characters in the string of characters to replace will be deleted from the source.
     * Any extra characters in the string of replacement characters will be ignored.
     *
     * @param o1 String to manipulate
     * @param o2 String of characters to replace
     * @param o3 String of replacement characters
     */
    private static String translate(Object o1, Object o2, Object o3) {
        String source = FunctionUtils.toString(o1);
        String from = FunctionUtils.toString(o2);
        String to = FunctionUtils.toString(o3);

        Hashtable<Character, Character> map = new Hashtable<>();
        for (int i = 0; i < Math.min(from.length(), to.length()); i++) {
            if (!map.containsKey(new Character(from.charAt(i)))) {
                map.put(new Character(from.charAt(i)), new Character(to.charAt(i)));
            }
        }
        String toDelete = from.substring(Math.min(from.length(), to.length()));

        String returnValue = "";
        for (int i = 0; i < source.length(); i++) {
            Character current = new Character(source.charAt(i));
            if (toDelete.indexOf(current) == -1) {
                if (map.containsKey(current)) {
                    current = map.get(current);
                }
                returnValue += current;
            }
        }

        return returnValue;
    }

    @Override
    public String getDocumentation() {
        return getDocHeader()
                + "Behavior:  Replace each of a given set of characters in one string with one of another set of characters.\n"
                + "Return: String with replacements made.\n"
                + "Arguments:  Three arguments\n"
                + "\tThe string to manipulate\n"
                + "\tThe set of characters to replace\n"
                + "\tThe set of replacement characters. Any occurrences of the first character in the second argument will be replaced by the first character in this argument; any occurrences of the second character in the second argument will be replaced by the second character in this argument; etc. If there are fewer replacement characters than characters to replace, the \"extra\" characters will be deleted. If there are fewer characters to replace than replacement characters, the \"extra\" replacement characters will be ignored.\n"
                + "Syntax: translate(text, to-replace, replacements)\n"
                + "Examples\n"
                + "\ttranslate('aBcdE', 'xyz', 'qrs') returns \"aBcdE\"\n"
                + "\ttranslate('bosco', 'bos', 'sfo') returns \"sfocf\"\n"
                + "\ttranslate('yellow', 'low', 'or') returns \"yeoor\"\n"
                + "\ttranslate('bora bora', 'a', 'bc') returns \"borb borb\"";
    }
}