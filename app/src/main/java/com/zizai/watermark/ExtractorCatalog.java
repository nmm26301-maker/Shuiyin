package com.zizai.watermark;

/** Ignore diagnostics but retain extractor names and the upstream broken flag. */
final class ExtractorCatalog {
    static boolean line(String value){
        if(value.startsWith(EngineCommand.GROUP_PREFIX)||value.contains(": "))return false;
        String name=value.endsWith(" (CURRENTLY BROKEN)")?value.substring(0,value.length()-19):value;
        return !name.isEmpty()&&name.matches("[\\p{L}\\p{N}_.:/+\\- ]+");
    }
}
