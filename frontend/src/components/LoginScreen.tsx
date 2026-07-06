import { useState } from 'react';

interface Props {
  onLogin: (username: string) => void;
}

type Tab = 'login' | 'register';

export function LoginScreen({ onLogin }: Props) {
  const [tab, setTab] = useState<Tab>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [startingElo, setStartingElo] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const switchTab = (t: Tab) => {
    setTab(t);
    setStartingElo('');
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    const endpoint = tab === 'login' ? '/api/auth/login' : '/api/auth/register';
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          password,
          ...(tab === 'register' && startingElo.trim() !== '' && !isNaN(parseInt(startingElo, 10))
            ? { startingElo: parseInt(startingElo, 10) }
            : {}),
        }),
      });
      if (res.ok) {
        const { username: u } = await res.json() as { username: string };
        onLogin(u);
      } else if (res.status >= 500) {
        setError('Something went wrong on the server. Please try again.');
      } else {
        const text = await res.text();
        setError(text || (tab === 'login' ? 'Invalid username or password' : 'Registration failed'));
      }
    } catch {
      setError('Could not connect to server');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="screen">
      <h2 className="login-title">Chess</h2>
      <div className="login-tabs">
        <button
          type="button"
          className={`login-tab${tab === 'login' ? ' login-tab-active' : ''}`}
          onClick={() => switchTab('login')}
        >
          Sign In
        </button>
        <button
          type="button"
          className={`login-tab${tab === 'register' ? ' login-tab-active' : ''}`}
          onClick={() => switchTab('register')}
        >
          Create Account
        </button>
      </div>
      <form className="login-form" onSubmit={handleSubmit}>
        <input
          className="login-input"
          value={username}
          onChange={e => setUsername(e.target.value)}
          placeholder="Username"
          autoComplete="username"
          required
        />
        <input
          className="login-input"
          type="password"
          value={password}
          onChange={e => setPassword(e.target.value)}
          placeholder="Password"
          autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
          required
        />
        {tab === 'register' && (
          <input
            className="login-input"
            type="number"
            value={startingElo}
            onChange={e => setStartingElo(e.target.value)}
            placeholder="Starting ELO (optional)"
            min={400}
            max={2800}
          />
        )}
        {error && <p className="login-error">{error}</p>}
        <button className="login-button" type="submit" disabled={loading}>
          {loading
            ? (tab === 'login' ? 'Signing in…' : 'Creating account…')
            : (tab === 'login' ? 'Play' : 'Register & Play')}
        </button>
      </form>
    </div>
  );
}
