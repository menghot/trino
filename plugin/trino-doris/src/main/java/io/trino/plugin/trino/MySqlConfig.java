/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.trino;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.units.Duration;

import javax.validation.constraints.Min;

import java.util.concurrent.TimeUnit;

public class MySqlConfig
{
    private boolean autoReconnect = true;
    private int maxReconnects = 3;
    private int datetimeColumnSize;
    private Duration connectionTimeout = new Duration(10, TimeUnit.SECONDS);

    // Using `useInformationSchema=true` prevents race condition inside MySQL driver's java.sql.DatabaseMetaData.getColumns
    // implementation, which throw SQL exception when a table disappears during listing.
    // Using `useInformationSchema=false` may provide more diagnostic information (see https://github.com/trinodb/trino/issues/1597)
    private boolean driverUseInformationSchema = true;

    public boolean isAutoReconnect()
    {
        return autoReconnect;
    }

    @Config("mysql.auto-reconnect")
    public MySqlConfig setAutoReconnect(boolean autoReconnect)
    {
        this.autoReconnect = autoReconnect;
        return this;
    }

    @Min(1)
    public int getMaxReconnects()
    {
        return maxReconnects;
    }

    @Config("mysql.max-reconnects")
    public MySqlConfig setMaxReconnects(int maxReconnects)
    {
        this.maxReconnects = maxReconnects;
        return this;
    }

    public int getDatetimeColumnSize()
    {
        return datetimeColumnSize;
    }

    @Config("mysql.datetime-column-size")
    @ConfigDescription("Value of datetime columnSize for special db like doris")
    public void setDatetimeColumnSize(int datetimeColumnSize)
    {
        this.datetimeColumnSize = datetimeColumnSize;
    }

    public Duration getConnectionTimeout()
    {
        return connectionTimeout;
    }

    @Config("mysql.connection-timeout")
    public MySqlConfig setConnectionTimeout(Duration connectionTimeout)
    {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public boolean isDriverUseInformationSchema()
    {
        return driverUseInformationSchema;
    }

    @Config("mysql.jdbc.use-information-schema")
    @ConfigDescription("Value of useInformationSchema MySQL JDBC driver connection property")
    public MySqlConfig setDriverUseInformationSchema(boolean driverUseInformationSchema)
    {
        this.driverUseInformationSchema = driverUseInformationSchema;
        return this;
    }
}
