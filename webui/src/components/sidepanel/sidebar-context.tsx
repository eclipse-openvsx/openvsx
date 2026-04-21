import { createContext } from 'react';


export const SidebarContext = createContext<{ collapsed: boolean; }>({ collapsed: false });
