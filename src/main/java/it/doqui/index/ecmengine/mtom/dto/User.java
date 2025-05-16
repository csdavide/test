/* Index ECM Engine - A system for managing the capture (when created 
 * or received), classification (cataloguing), storage, retrieval, 
 * revision, sharing, reuse and disposition of documents.
 *
 * Copyright (C) 2008 Regione Piemonte
 * Copyright (C) 2008 Provincia di Torino
 * Copyright (C) 2008 Comune di Torino
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package it.doqui.index.ecmengine.mtom.dto;

/**
 * <p>
 * DTO che rappresenta un utente di ECMEngine.
 * </p>
 * 
 * @author DoQui
 */
public class User extends MtomEngineDto {

    private static final long serialVersionUID = 7704835740225218018L;

    private String nome;
    private String cognome;
    private String nomeUtente;
    private String idOrganizzazione;
    private String password;
    private String homeFolderPath;

    /**
     * Costruttore predefinito.
     */
    public User() {
	super();
    }

    @Deprecated
    public User(String nome, String cognome, String nomeUtente) {
	this.nome = nome;
	this.cognome = cognome;
	this.nomeUtente = nomeUtente;
    }

    @Deprecated
    public String getOrganizationId() {
	return idOrganizzazione;
    }

    @Deprecated
    public void setOrganizationId(String orgId) {
	this.idOrganizzazione = orgId;
    }

    /**
     * Restituisce la password dell'utente.
     */
    public String getPassword() {
	return password;
    }

    /**
     * Imposta la password dell'utente.
     */
    public void setPassword(String password) {
	this.password = password;
    }

    /**
     * Restituisce il nome anagrafico dell'utente.
     */
    public String getName() {
	return nome;
    }

    /**
     * Imposta il nome anagrafico dell'utente.
     */
    public void setName(String name) {
	this.nome = name;
    }

    /**
     * Restituisce il cognome anagrafico dell'utente.
     */
    public String getSurname() {
	return cognome;
    }

    /**
     * Imposta il cognome anagrafico dell'utente.
     */
    public void setSurname(String surname) {
	this.cognome = surname;
    }

    /**
     * Restituisce il nome utente applicativo.
     */
    public String getUsername() {
	return nomeUtente;
    }

    /**
     * Imposta il nome utente applicativo.
     */
    public void setUsername(String username) {
	this.nomeUtente = username;
    }

    /**
     * Restituisce il percorso della user home dell'utente.
     */
    public String getHomeFolderPath() {
	return homeFolderPath;
    }

    /**
     * Imposta il percorso della user home dell'utente.
     */
    public void setHomeFolderPath(String homeFolderPath) {
	this.homeFolderPath = homeFolderPath;
    }
}
