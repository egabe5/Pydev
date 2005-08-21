/*
 * Created on 24/07/2005
 */
package com.python.pydev.analysis;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.osgi.service.prefs.Preferences;

public class AnalysisPreferenceInitializer extends AbstractPreferenceInitializer{

    public static final String DEFAULT_SCOPE = "com.python.pydev.analysis";
    
    public static final String SEVERITY_UNUSED_VARIABLE = "SEVERITY_UNUSED_VARIABLE";
    public static final int DEFAULT_SEVERITY_UNUSED_VARIABLE = IMarker.SEVERITY_WARNING;
    
    public static final String NAMES_TO_IGNORE_UNUSED_VARIABLE = "NAMES_TO_IGNORE_UNUSED_VARIABLE";
    public static final String DEFAULT_NAMES_TO_IGNORE_UNUSED_VARIABLE = "dummy, _, unused";
    
    public static final String SEVERITY_UNUSED_IMPORT = "SEVERITY_UNUSED_IMPORT";
    public static final int DEFAULT_SEVERITY_UNUSED_IMPORT = IMarker.SEVERITY_WARNING;

    public static final String SEVERITY_UNDEFINED_VARIABLE = "SEVERITY_UNDEFINED_VARIABLE";
    public static final int DEFAULT_SEVERITY_UNDEFINED_VARIABLE = IMarker.SEVERITY_ERROR;
    
    public static final String SEVERITY_DUPLICATED_SIGNATURE = "SEVERITY_DUPLICATED_SIGNATURE";
    public static final int DEFAULT_SEVERITY_DUPLICATED_SIGNATURE = IMarker.SEVERITY_ERROR;
    
    public static final String SEVERITY_REIMPORT = "SEVERITY_REIMPORT";
    public static final int DEFAULT_SEVERITY_REIMPORT = IMarker.SEVERITY_WARNING;
    
    public static final String SEVERITY_UNRESOLVED_IMPORT = "SEVERITY_UNRESOLVED_IMPORT";
    public static final int DEFAULT_SEVERITY_UNRESOLVED_IMPORT = IMarker.SEVERITY_ERROR;
    

    @Override
    public void initializeDefaultPreferences() {
        Preferences node = new DefaultScope().getNode(DEFAULT_SCOPE);
        
        for (int i = 0; i < AnalysisPreferences.completeSeverityMap.length; i++) {
            Object[] s = AnalysisPreferences.completeSeverityMap[i];
            node.putInt((String)s[1], (Integer)s[2]);
            
        }
        node.put   (NAMES_TO_IGNORE_UNUSED_VARIABLE, DEFAULT_NAMES_TO_IGNORE_UNUSED_VARIABLE);
    }



}
