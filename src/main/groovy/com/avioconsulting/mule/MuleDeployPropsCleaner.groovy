package com.avioconsulting.mule

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MuleDeployPropsCleaner {
    static def cleanProps(File muleDeployPropsFile) {
        def separator = System.getProperty('line.separator')
        def lines = muleDeployPropsFile.text.split(separator)
        def linesWithoutTimeStamp = lines.findAll { line ->
            // it's not a date
            if (!line.startsWith('#')) {
                return true
            }
            // trim off the # and any white space
            line = line[1..-1].trim()
            try {
                // Mule uses this format - Tue Oct 23 13:13:13 MDT 2018
                ZonedDateTime.parse(line,
                                    DateTimeFormatter.ofPattern('EEE MMM dd HH:mm:ss z yyyy'))
                return false
            }
            catch (e) {
                return true
            }
        }
        muleDeployPropsFile.write(linesWithoutTimeStamp.join(separator))
    }
}
