package se.bricole.xss.server;

/**
 * A Registrar module is a module that needs access to the
 * Configuration object outside client access.
 *
 * It was originally intended only as a mechanism to let module load
 * other modules at arbitrarytimes during their life cycle.
 *
 * setConfiguration() is called by the module loader before
 * setProperties() (so that setProperties() still can serve as a point
 * of initialization).
 *
 */
public interface ModuleRegistrar extends Module {

    public static int TYPE = REGISTRAR;

    public void setConfiguration(Configuration config);
    
}
