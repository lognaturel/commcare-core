package org.javarosa.xpath.expr;

import org.commcare.util.LogTypes;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.InFormExpressionCacher;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.analysis.AnalysisInvalidException;
import org.javarosa.xpath.analysis.ContainsUncacheableExpressionAnalyzer;
import org.javarosa.xpath.analysis.ReferencesMainInstanceAnalyzer;
import org.javarosa.xpath.analysis.XPathAnalyzable;

/**
 * Superclass for an XPathExpression that keeps track of all information related to if it can be
 * cached, and contains wrapper functions for all caching operations.
 *
 * @author Aliza Stone
 */
public abstract class InFormCacheableExpr implements XPathAnalyzable {

    private Object justRetrieved;
    protected boolean computedCacheability;
    protected boolean isCacheable;

    private static InFormExpressionCacher cacher = new InFormExpressionCacher();

    protected boolean isCached(EvaluationContext ec) {
        if (ec.cachingIsAllowed()) {
            queueUpCachedValue();
            return justRetrieved != null;
        } else {
            // this is the best signal we have for knowing to clear
            if (cacher.hasCachedValues()) {
                cacher.clearCache();
            }
            return false;
        }
    }

    private void queueUpCachedValue() {
        justRetrieved = cacher.getCachedValue(this);
    }

    Object getCachedValue() {
        return justRetrieved;
    }

     void cache(Object value, EvaluationContext ec) {
        if (ec.cachingIsAllowed() && isCacheable(ec)) {
            cacher.cache(this, value);
        }
    }

    protected boolean isCacheable(EvaluationContext ec) {
        if (!computedCacheability) {
            computeCacheability(ec);
        }
        return isCacheable;
    }

    private void computeCacheability(EvaluationContext ec) {
        if (ec.getMainInstance() instanceof FormInstance) {
            try {
                isCacheable = !referencesMainFormInstance((FormInstance)ec.getMainInstance(), ec) &&
                        !containsUncacheableSubExpression(ec);
            } catch (AnalysisInvalidException e) {
                // if the analysis didn't complete then we assume it's not cacheable
                isCacheable = false;
            }
            computedCacheability = true;
        } else {
            Logger.log(LogTypes.SOFT_ASSERT,
                    "Caching was enabled in the ec, but the main instance provided " +
                            "to InFormCacheableExpr by the ec was not of type FormInstance");
            isCacheable = false;
        }
    }

    private boolean referencesMainFormInstance(FormInstance formInstance, EvaluationContext ec) throws AnalysisInvalidException {
        String formInstanceRoot = formInstance.getBase().getChildAt(0).getName();
        return (new ReferencesMainInstanceAnalyzer(formInstanceRoot, ec)).computeResult(this);
    }

    private boolean containsUncacheableSubExpression(EvaluationContext ec) throws AnalysisInvalidException {
        return (new ContainsUncacheableExpressionAnalyzer(ec)).computeResult(this);
    }

}
