/**
 * Generic modal dialog component for overlay interactions.
 * Renders a backdrop and centered dialog frame with header, title, close button,
 * and arbitrary children content.
 */

import React, { type ReactNode } from 'react';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

/**
 * Renders an accessible modal overlay with header title, close dismiss trigger, and slotted content.
 *
 * @param props Component properties containing the modal title, close handler, and dialog body content.
 * @returns Rendered JSX modal dialog elements.
 */
export const Modal: React.FC<ModalProps> = ({ title, onClose, children }) => {
  return (
    <>
      <div className="modal-backdrop show" style={{ opacity: 0.5 }}></div>
      <div className="modal show d-block" tabIndex={-1} role="dialog">
        <div className="modal-dialog modal-dialog-centered" role="document">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{title}</h5>
              <button type="button" className="btn-close" onClick={onClose} aria-label="Close"></button>
            </div>
            {children}
          </div>
        </div>
      </div>
    </>
  );
};
