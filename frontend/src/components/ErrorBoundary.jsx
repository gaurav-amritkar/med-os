import { Component } from 'react';
import Icon from './icons';

/**
 * Error Boundary component to catch JavaScript errors anywhere in the component tree.
 * Logs errors and displays a fallback UI instead of crashing the whole app.
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(_error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({ error, errorInfo });
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  resetErrorBoundary = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    if (this.props.fallback) {
      return this.props.fallback(this.state.error, this.resetErrorBoundary);
    }

    return (
      <div className="error-screen">
        <div className="card error-card">
          <div className="error-icon">
            <Icon name="alert" size={36} />
          </div>

          <h2>This page stopped working</h2>
          <p className="error-copy">
            Reload the page to try again. If it keeps happening, tell your
            system administrator.
          </p>

          <div className="error-actions">
            <button type="button" className="btn-primary" onClick={this.resetErrorBoundary}>
              Try again
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => { window.location.href = '/login'; }}
            >
              Go to sign in
            </button>
          </div>

          {import.meta.env.DEV && this.state.error && (
            <details className="error-details">
              <summary>Error details (development only)</summary>
              <pre>
                {this.state.error?.toString()}
                {this.state.errorInfo?.componentStack &&
                  `\n\n${this.state.errorInfo.componentStack}`}
              </pre>
            </details>
          )}
        </div>
      </div>
    );
  }
}

export default ErrorBoundary;
