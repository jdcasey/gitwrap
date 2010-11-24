/*
 *  Copyright (C) 2010 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.commonjava.gitwrap;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;

public final class TestUtils
{

    private TestUtils()
    {
    }

    public static void configureLogging()
    {
        final Configurator log4jConfigurator = new Configurator()
        {
            @SuppressWarnings( "unchecked" )
            public void doConfigure( final URL notUsed, final LoggerRepository repo )
            {
                final ConsoleAppender appender = new ConsoleAppender( new SimpleLayout() );
                appender.setImmediateFlush( true );
                appender.setThreshold( Level.ALL );

                repo.getRootLogger().addAppender( appender );

                final Enumeration<Logger> loggers = repo.getCurrentLoggers();
                while ( loggers.hasMoreElements() )
                {
                    final Logger logger = loggers.nextElement();
                    logger.addAppender( appender );
                    logger.setLevel( Level.INFO );
                }
            }
        };

        log4jConfigurator.doConfigure( null, LogManager.getLoggerRepository() );
    }

    public static void delete( final File f )
    {
        if ( f == null || !f.exists() )
        {
            return;
        }

        if ( f.isDirectory() )
        {
            final File[] files = f.listFiles();
            for ( final File file : files )
            {
                delete( file );
            }
        }

        f.delete();
    }
}
