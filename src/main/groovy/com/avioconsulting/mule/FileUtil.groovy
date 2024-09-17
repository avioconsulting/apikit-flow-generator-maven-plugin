package com.avioconsulting.mule

import java.nio.file.Files
import java.nio.file.Path

trait FileUtil {
    static File join(File parent, String... parts) {
        def separator = System.getProperty 'file.separator'
        new File(parent, parts.join(separator))
    }

    /* this is used to determine if the file is binary, ie. don't modify it */
    static boolean isText(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path)
            for (byte b : bytes) {
                // Non-printable bytes (excluding common control characters) indicate binary content
                if (b < 32 && b != 9 && b != 10 && b != 13) {
                    return false
                }
            }
            return true
        } catch (IOException e) {
            e.printStackTrace()
            return true
        }
    }
}