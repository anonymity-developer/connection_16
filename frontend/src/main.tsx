import { ParticipationProvider } from './features/participation/context';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { AuthProvider } from './features/auth/context';
import './styles/global.css';

createRoot(document.getElementById('root')!).render(<StrictMode><AuthProvider><ParticipationProvider><App /></ParticipationProvider></AuthProvider></StrictMode>);
