/**
 * Generic container components for card-based UI layouts.
 * Provides modular Card, CardHeader, and CardBody components styled using Bootstrap utilities
 * and shadow elevations.
 */

import React, { type ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  testId?: string;
}

/**
 * Top-level card wrapper providing shadow styling and border structure.
 *
 * @param props Component properties including children, optional custom CSS classes, and test ID.
 * @returns Rendered JSX card container element.
 */
export const Card: React.FC<CardProps> = ({ children, className = '', testId }) => (
  <div className={`card shadow-sm ${className}`} data-testid={testId}>
    {children}
  </div>
);

interface CardHeaderProps {
  children: ReactNode;
  className?: string;
}

/**
 * Card header container for holding titles, action buttons, and badge indicators.
 *
 * @param props Component properties including header content children and optional CSS classes.
 * @returns Rendered JSX card header element.
 */
export const CardHeader: React.FC<CardHeaderProps> = ({ children, className = '' }) => (
  <div className={`card-header ${className}`}>
    {children}
  </div>
);

interface CardBodyProps {
  children: ReactNode;
  className?: string;
  style?: React.CSSProperties;
}

/**
 * Content container component for card body sections, supporting custom layout and styling.
 *
 * @param props Component properties including body children, optional classes, and inline styles.
 * @returns Rendered JSX card body element.
 */
export const CardBody: React.FC<CardBodyProps> = ({ children, className = '', style }) => (
  <div className={`card-body ${className}`} style={style}>
    {children}
  </div>
);
