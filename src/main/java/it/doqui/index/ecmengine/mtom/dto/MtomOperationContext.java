package it.doqui.index.ecmengine.mtom.dto;

/**
 * Classe DTO che rappresenta il contesto applicativo per l'autenticazione su
 * ECMENGINE.
 * 
 * @author DoQui
 *
 */
public class MtomOperationContext {
    private String username;
    private String password;
    private String nomeFisico;
    private String fruitore;
    private String repository;

    public MtomOperationContext() {
	super();
	// TODO Auto-generated constructor stub
    }

    public MtomOperationContext(String username, String password, String nomeFisico, String fruitore,
	    String repository) {
	super();
	this.username = username;
	this.password = password;
	this.nomeFisico = nomeFisico;
	this.fruitore = fruitore;
	this.repository = repository;
    }

    /**
     * Restituisce lo username dell'utente applicativo per l'autenticazione
     * sull'ECMENGINE.
     *
     * @return Lo username.
     */
    public String getUsername() {
	return username;
    }

    /**
     * Imposta lo username dell'utente applicativo per l'autenticazione
     * sull'ECMENGINE.
     *
     * @param username Lo username per l'autenticazione sull'ECMENGINE.
     */
    public void setUsername(String username) {
	this.username = username;
    }

    /**
     * Restituisce la password dell'utente applicativo per l'autenticazione
     * sull'ECMENGINE.
     *
     * @return La password.
     */
    public String getPassword() {
	return password;
    }

    /**
     * Imposta la password dell'utente applicativo per l'autenticazione
     * sull'ECMENGINE.
     *
     * @param password La password per l'autenticazione sull'ECMENGINE.
     */
    public void setPassword(String password) {
	this.password = password;
    }

    /**
     * Restituisce il nome fisico dell'utente che sta eseguendo l'operazione
     * sull'ECMENGINE.
     *
     * @return Il nome fisico, oppure {@code null} se non &egrave; stato impostato
     *         alcun nome fisico.
     */
    public String getNomeFisico() {
	return nomeFisico;
    }

    /**
     * Imposta il nome fisico dell'utente che sta eseguendo l'operazione
     * sull'ECMENGINE.
     *
     * <p>
     * Questo metodo accetta in input il valore {@code null} per eliminare il nome
     * eventualmente impostato precedentemente.
     * </p>
     *
     * @param nomeFisico Il nome fisico.
     */
    public void setNomeFisico(String nomeFisico) {
	this.nomeFisico = nomeFisico;
    }

    /**
     * Restituisce il nome del fruitore che sta invocando il servizio
     * sull'ECMENGINE.
     *
     * @return Il nome del fruitore.
     */
    public String getFruitore() {
	return fruitore;
    }

    /**
     * Imposta il nome del fruitore che sta invocando il servizio sull'ECMENGINE.
     *
     * @param fruitore Il nome del fruitore.
     */
    public void setFruitore(String fruitore) {
	this.fruitore = fruitore;
    }

    /**
     * Restituisce il nome del repository fisico su cui operare.
     *
     * @return Il nome del repository.
     */
    public String getRepository() {
	return repository;
    }

    /**
     * Imposta il nome del repository fisico su cui operare.
     *
     * @param repository Il nome del repository.
     */
    public void setRepository(String repository) {
	this.repository = repository;
    }
}
