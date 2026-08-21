import React, { type ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  testId?: string;
}

export const Card: React.FC<CardProps> = ({ children, className = '', testId }) => (
  <div className={`card shadow-sm ${className}`} data-testid={testId}>
    {children}
  </div>
);

interface CardHeaderProps {
  children: ReactNode;
  className?: string;
}

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

export const CardBody: React.FC<CardBodyProps> = ({ children, className = '', style }) => (
  <div className={`card-body ${className}`} style={style}>
    {children}
  </div>
);
