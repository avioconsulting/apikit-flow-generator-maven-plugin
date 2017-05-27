package com.avioconsulting.mule

trait FileUtil {
    static File join(File parent, String... parts) {
        def separator = System.getProperty 'file.separator'
        new File(parent, parts.join(separator))
    }
}