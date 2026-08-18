import { Component } from 'react';

/**
 * Error Boundary component to catch JavaScript errors anywhere in the component tree.
 * Logs errors and displays a fallback UI instead of crashing the whole app.
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({
      error: error,
      errorInfo: errorInfo
    });
    
    // Log error to console (in production, send to error reporting service)
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      // If custom fallback is provided, render it
      if (this.props.fallback) {
        return this.props.fallback(this.state.error, this.resetErrorBoundary);
      }
      
      // Default fallback UI
      return (
        <div style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 20,
          background: 'var(--bg-deep)',
          color: 'var(--text)'
        }}>
          <div className="card" style={{ maxWidth: 500, padding: 32, textAlign: 'center' }}>
            <div style={{
              width: 80, height: 80, margin: '0 auto 24',
              borderRadius: '50%', background: 'rgba(239,68,68,0.15)',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </div>
            <h2 style={{ fontSize: '1.5rem', marginBottom: 12 }}>Something went wrong</h2>
            <p style={{ color: 'var(--text-dim)', marginBottom: 24, lineHeight: 1.6 }}>
              We're sorry, but an unexpected error occurred. Our team has been notified.
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button 
                className="btn-primary"
                onClick={this.resetErrorBoundary}
              >
                Try Again
              </button>
              <button 
                className="btn-secondary"
                onClick={() => window.location.href = '/login'}
              >
                Go to Login
              </button>
            </div>
            {process.env.NODE_ENV === 'development' && this.state.error && (
              <details style={{ marginTop: 24, textAlign: 'left', fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                <summary>Error Details (Development)</summary>
                <pre style={{ 
                  marginTop: 12, padding: 12, background: 'var(--surface-2)', 
                  borderRadius: 'var(--radius-sm)', overflow: 'auto', maxHeight: 200 
                }}>
                  {this.state.error?.toString()}
                  {this.state.errorInfo?.componentStack && `\n\n${this.state.errorInfo.componentStack}`}
                </pre>
              </details>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }

  resetErrorBoundary = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };
}

export default ErrorBoundary;