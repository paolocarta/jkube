/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.kit.build.service.docker;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.jkube.kit.build.service.docker.access.DockerAccess;
import org.eclipse.jkube.kit.build.service.docker.access.DockerConnectionDetector;
import org.eclipse.jkube.kit.build.service.docker.access.DockerMachine;
import org.eclipse.jkube.kit.build.service.docker.access.hc.DockerAccessWithHcClient;
import org.eclipse.jkube.kit.build.service.docker.config.DockerMachineConfiguration;
import org.eclipse.jkube.kit.common.KitLogger;

public class DockerAccessFactory {

    public DockerAccess createDockerAccess(DockerAccessContext dockerAccessContext) {

        try {
            DockerConnectionDetector dockerConnectionDetector = createDockerConnectionDetector(dockerAccessContext, dockerAccessContext.getLog());
            DockerConnectionDetector.ConnectionParameter connectionParam =
                    dockerConnectionDetector.detectConnectionParameter(dockerAccessContext.getDockerHost(), dockerAccessContext.getCertPath());
            DockerAccess access = new DockerAccessWithHcClient(connectionParam.getUrl(),
                    connectionParam.getCertPath(),
                    dockerAccessContext.getMaxConnections(),
                    dockerAccessContext.getLog());
            access.start();
            setDockerHostAddressProperty(dockerAccessContext, connectionParam.getUrl());
            return access;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create docker access object ", e);
        }

    }

    private DockerConnectionDetector createDockerConnectionDetector(DockerAccessContext dockerAccessContext, KitLogger log) {
        return new DockerConnectionDetector(getDockerHostProviders(dockerAccessContext, log));
    }

    private List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders(DockerAccessContext dockerAccessContext, KitLogger log) {
        if (dockerAccessContext.getDockerHostProviders() != null) {
            return dockerAccessContext.getDockerHostProviders();
        }

        return getDefaultDockerHostProviders(dockerAccessContext, log);
    }

    /**
     * Return a list of providers which could delive connection parameters from
     * calling external commands. For this plugin this is docker-machine, but can be overridden
     * to add other config options, too.
     *
     * @return list of providers or <code>null</code> if none are applicable
     */
    private List<DockerConnectionDetector.DockerHostProvider> getDefaultDockerHostProviders(DockerAccessContext dockerAccessContext, KitLogger log) {

        DockerMachineConfiguration config = dockerAccessContext.getMachine();
        if (dockerAccessContext.isSkipMachine()) {
            config = null;
        } else if (config == null) {
            Properties projectProps = dockerAccessContext.getProjectProperties();
            if (projectProps.containsKey(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP)) {
                config = new DockerMachineConfiguration(
                    projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_NAME_PROP),
                    projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_AUTO_CREATE_PROP),
                    projectProps.getProperty(DockerMachineConfiguration.DOCKER_MACHINE_REGENERATE_CERTS_AFTER_START_PROP));
            }
        }

        List<DockerConnectionDetector.DockerHostProvider> ret = new ArrayList<>();
        ret.add(new DockerMachine(log, config));
        return ret;
    }

    // Registry for managed containers
    private void setDockerHostAddressProperty(DockerAccessContext dockerAccessContext, String dockerUrl) {
        Properties props = dockerAccessContext.getProjectProperties();
        if (props.getProperty("docker.host.address") == null) {
            final String host;
            try {
                URI uri = new URI(dockerUrl);
                if (uri.getHost() == null && (uri.getScheme().equals("unix") || uri.getScheme().equals("npipe"))) {
                    host = "localhost";
                } else {
                    host = uri.getHost();
                }
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Cannot parse " + dockerUrl + " as URI: " + e.getMessage(), e);
            }
            props.setProperty("docker.host.address", host == null ? "" : host);
        }
    }

    // ===========================================

    public static class DockerAccessContext implements Serializable {

        private Properties projectProperties;

        private DockerMachineConfiguration machine;

        private List<DockerConnectionDetector.DockerHostProvider> dockerHostProviders;

        private boolean skipMachine;

        private String minimalApiVersion;

        private String dockerHost;

        private String certPath;

        private int maxConnections;

        private KitLogger log;

        public DockerAccessContext() {
        }

        public Properties getProjectProperties() {
            return projectProperties;
        }

        public DockerMachineConfiguration getMachine() {
            return machine;
        }

        public List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders() {
            return dockerHostProviders;
        }

        public boolean isSkipMachine() {
            return skipMachine;
        }

        public String getMinimalApiVersion() {
            return minimalApiVersion;
        }

        public String getDockerHost() {
            return dockerHost;
        }

        public String getCertPath() {
            return certPath;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public KitLogger getLog() {
            return log;
        }

        public static class Builder {

            private DockerAccessContext context = new DockerAccessContext();

            public Builder() {
                this.context = new DockerAccessContext();
            }

            public Builder(DockerAccessContext context) {
                this.context = context;
            }

            public Builder projectProperties(Properties projectProperties) {
                context.projectProperties = projectProperties;
                return this;
            }

            public Builder machine(DockerMachineConfiguration machine) {
                context.machine = machine;
                return this;
            }

            public Builder dockerHostProviders(List<DockerConnectionDetector.DockerHostProvider> dockerHostProviders) {
                context.dockerHostProviders = dockerHostProviders;
                return this;
            }

            public Builder skipMachine(boolean skipMachine) {
                context.skipMachine = skipMachine;
                return this;
            }

            public Builder minimalApiVersion(String minimalApiVersion) {
                context.minimalApiVersion = minimalApiVersion;
                return this;
            }

            public Builder dockerHost(String dockerHost) {
                context.dockerHost = dockerHost;
                return this;
            }

            public Builder certPath(String certPath) {
                context.certPath = certPath;
                return this;
            }

            public Builder maxConnections(int maxConnections) {
                context.maxConnections = maxConnections;
                return this;
            }

            public Builder log(KitLogger log) {
                context.log = log;
                return this;
            }

            public DockerAccessContext build() {
                return context;
            }

        }
    }

}
