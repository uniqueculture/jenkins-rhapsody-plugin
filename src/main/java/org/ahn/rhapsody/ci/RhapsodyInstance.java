/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ahn.rhapsody.ci;

/**
 *
 * @author me
 */
public class RhapsodyInstance {
    
    final String apiProtocol;
    final String host;
    final int apiPort;

    public RhapsodyInstance(String apiProtocol, String host, int apiPort) {
        this.apiProtocol = apiProtocol;
        this.host = host;
        this.apiPort = apiPort;
    }

    public RhapsodyInstance(String host, int apiPort) {
        this("https", host, apiPort);
    }
    
    public String getBaseUrl() {
        return apiProtocol + "://" + host + ":" + apiPort;
    }
    
    
}
