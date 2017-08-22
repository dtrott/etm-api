/*
 * Copyright 2011 Edmunds.com, Inc.
 *
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
package com.edmunds.etm.loadbalancer.api;

import com.edmunds.etm.management.api.HostAddress;
import com.edmunds.etm.management.api.HttpMonitor;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A LoadBalancerConnection provides a connection to a load balancer that allows a basic set of operations.
 */
public interface LoadBalancerConnection {

    /**
     * Initiates a connection to the load balancer.
     * <p>
     * Typically this is used to validate login credentials and/or to refresh them.<p/>
     * The method is called before a batch of changes is made before a call to getAvailabilityStatus().
     *
     * @return true if the connection was successful. A false return causes ETM to abort the changes it was about to attempt.
     */
    boolean connect();

    /**
     * Indicates if the currenly connected load balancer is the active one in a HA pair.
     * <p>
     * ETM will only apply changes if it is connected to the active load balancer.<p/>
     * However ETM doesn't perform this check when displaying the status of the the VIPS / Virtual Servers.
     * If the backup loadbalancer doesn't contain any useful information it is recommended that the connect() method
     * return false if the connection is made to the backup load balancer.
     *
     * @return true if the connection is to the active loadbalancer in a HA pair.
     */
    boolean isActive();

    /**
     * Fetches the currently defined set of virtual servers from the load balancer.
     * <p>
     * Each virtual server entry should also include any pool members.
     * <p>
     * Note: ETM doesn't need to know about virtual servers it is not managing, hence these should be omitted.
     *
     * @return the current set of virtual servers.
     * @throws RemoteException if a problem occurs connecting to the load balancer.
     */
    Set<VirtualServer> getAllVirtualServers() throws RemoteException;

    /**
     * Fetches the details of a defined virtual server.
     * <p>
     * The virtual server entry should also include any pool members.
     * <p>
     * Note: This method is not currently used by ETM.
     *
     * @param serverName the server name to lookup.
     * @return the details of the virtual server.
     * @throws VirtualServerNotFoundException if the virtual server doesn't exist.
     * @throws RemoteException if a problem occurs connecting to the load balancer.
     */
    VirtualServer getVirtualServer(String serverName)
            throws VirtualServerNotFoundException, RemoteException;

    /**
     * Quick check to see if a virtual server is defined.
     * <p>
     * Currently ETM does not call this method - it may be used in future.
     *
     * @param serverName the name of the server to check.
     * @return true if the virtual server is defined.
     * @throws RemoteException if a problem occurs connecting to the load-balancer.
     */
    boolean isVirtualServerDefined(String serverName) throws RemoteException;

    /**
     * @param serverNames
     * @return
     * @throws VirtualServerNotFoundException
     * @throws RemoteException
     */
    Map<String, AvailabilityStatus> getAvailabilityStatus(List<String> serverNames)
            throws VirtualServerNotFoundException, RemoteException;

    /**
     * Creates a virtual server.
     * <p>
     * The creation of a virtual server is initiated when the first client connects.
     * As a result it is necessary to call server.getPoolMembers() in order to bind the first client.
     * A seperate call to addPoolMember() will not be made for any pool members included in this call.
     *
     * @param server              the server to be created.
     * @param virtualServerConfig the port number to use for the newly created virtual server.
     * @param httpMonitor         option health-check to define for the server (may be null).
     * @return the HostAddress of the newly created virtual server.
     * @throws VirtualServerExistsException if the server already exists.
     *                                      ETM will not use any existing servers and will keep trying to create it.
     * @throws RemoteException              if there is a problem creating the server.
     *                                      ETM will attempt to retry/create the server on the next update.
     */
    HostAddress createVirtualServer(
            VirtualServer server, VirtualServerConfig virtualServerConfig,
            HttpMonitor httpMonitor) throws VirtualServerExistsException, RemoteException;

    /**
     * Request to check the Virtual server configuration is correct.
     * <p>
     * This method is called once (per virtual server) when ETM starts up and again
     * if the active ETM controller changes (failover event).
     * <p/>
     * The purpose of the call is to ensure ETM's internal state is consistent with the state on the load balancer.
     * ETM pulls the list of virtual servers and pool members as part of the getAllVirtualServers() call,
     * hence this method only needs to check the health check and any detailed parameters are correct,
     * it doesn't need to add or remove pool members.
     *
     * @param server      the virtual server to check.
     * @param httpMonitor the health check that should be defined.
     */
    void verifyVirtualServer(VirtualServer server, HttpMonitor httpMonitor);

    /**
     * Deletes a virtual server.
     * <p>
     * There may still be pool members attached to the virtual server, they should be deleted as part of this call.
     * A seperate call to removeMember will not be made for the last pool member, hence  server.getPoolMembers() should
     * be called to get the list of members to be deleted.
     *
     * @param server the server to be deleted.
     * @throws VirtualServerNotFoundException if the virtual server is not found - this is logged, but otherwise ignored.
     * @throws RemoteException                causes ETM to skip this member, ETM will try to add the virtual server on the next update.
     */
    void deleteVirtualServer(VirtualServer server)
            throws VirtualServerNotFoundException, RemoteException;

    /**
     * Adds a member to an existing virtual server.
     *
     * @param serverName the name of the server to add too.
     * @param member     the pool member to add.
     * @throws PoolMemberExistsException if the pool member already exists - this is logged, but otherwise ignored.
     * @throws RemoteException           causes ETM to skip this member, ETM will try to add the member on the next update.
     */
    void addPoolMember(String serverName, PoolMember member)
            throws PoolMemberExistsException, RemoteException;

    /**
     * Removes a member from the pool / virtual server.
     *
     * @param serverName the name of the server which will be unbound.
     * @param member     the member to be removed.
     * @throws PoolMemberNotFoundException if the pool member is not found - this is logged, but otherwise ignored.
     * @throws RemoteException             causes ETM to skip this member, ETM will try to remove the member on the next update.
     */
    void removePoolMember(String serverName, PoolMember member)
            throws PoolMemberNotFoundException, RemoteException;

    /**
     * Commits in memory configuration to disk/firmware.
     * <p>
     * Some load-balancers have a feature where changes are initially made in memory and need to be persisted to disk.
     * After checking the current load balancer is still active.
     * ETM will call this method after all changes have been made, so that all changes can be persisted.
     *
     * @return true if the configuration has been persisted - currently ETM ignores the return value.
     */
    boolean saveConfiguration();
}
