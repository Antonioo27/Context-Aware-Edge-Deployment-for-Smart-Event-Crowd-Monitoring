package it.unibo.cas.eventanalysis.exception;

/**
 * The received payload does not respect the simulator's contract.
 */
public class InvalidBatchException extends RuntimeException {
    
    public InvalidBatchException(String message) {
        super(message);
    }
    
    public InvalidBatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
