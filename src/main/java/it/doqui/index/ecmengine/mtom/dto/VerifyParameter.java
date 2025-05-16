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

import java.util.Date;

/**
 * DTO che rappresenta i parametri di verifica.
 * 
 * @author DoQui
 */
public class VerifyParameter extends MtomEngineDto {
    private static final long serialVersionUID = -3614565130880901125L;
    private Date verificationDate;
    private int verificationType;
    private int profileType;
    private int verificationScope;

    /**
     * Restituisce la data di verifica.
     */
    public Date getVerificationDate() {
	return verificationDate;
    }

    /**
     * Imposta la data di verifica.
     */
    public void setVerificationDate(Date verificationDate) {
	this.verificationDate = verificationDate;
    }

    /**
     * Restituisce il tipo di verifica.
     */
    public int getVerificationType() {
	return verificationType;
    }

    /**
     * Imposta il tipo di verifica.
     */
    public void setVerificationType(int verificationType) {
	this.verificationType = verificationType;
    }

    /**
     * Resituisce il tipo di profilo.
     */
    public int getProfileType() {
	return profileType;
    }

    /**
     * Imposta il tipo di profilo.
     */
    public void setProfileType(int profileType) {
	this.profileType = profileType;
    }

    /**
     * Restitiusce il tipo di certificato.
     */
    public int getVerificationScope() {
	return verificationScope;
    }

    /**
     * Imposta il tipo di certificato.
     */
    public void setVerificationScope(int verificationScope) {
	this.verificationScope = verificationScope;
    }
}
