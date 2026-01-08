import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { SidebarComponent } from './layout/sidebar/sidebar.component';
import { HeaderComponent } from './layout/header/header.component';
import { AuthService } from './core/services/auth.service';
import { Subscription, filter } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent, HeaderComponent],
  template: `
    @if (showLayout) {
      <div class="flex h-screen overflow-hidden">
        <app-sidebar></app-sidebar>
        <div class="flex-1 flex flex-col overflow-hidden">
          <app-header></app-header>
          <main class="flex-1 overflow-y-auto p-6 bg-secondary-50 dark:bg-secondary-900">
            <router-outlet></router-outlet>
          </main>
        </div>
      </div>
    } @else {
      <router-outlet></router-outlet>
    }
  `,
  styles: []
})
export class AppComponent implements OnInit, OnDestroy {
  private router = inject(Router);
  private authService = inject(AuthService);

  showLayout = true;
  private routerSub?: Subscription;

  constructor() {
    // Initialize dark mode from localStorage
    const theme = localStorage.getItem('theme');
    if (theme === 'dark' || (!theme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      document.documentElement.classList.add('dark');
    }
  }

  ngOnInit(): void {
    // Check current route on init
    this.updateLayoutVisibility(this.router.url);

    // Update on navigation
    this.routerSub = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event) => {
      this.updateLayoutVisibility((event as NavigationEnd).urlAfterRedirects);
    });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  private updateLayoutVisibility(url: string): void {
    // Hide layout on login page
    this.showLayout = !url.startsWith('/login');
  }
}
