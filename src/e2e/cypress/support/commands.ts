/// <reference types="cypress" />
// ***********************************************
// This example commands.ts shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })
//

declare namespace Cypress {
  interface Chainable<Subject = any> {
    loginViaAAD(username: string, password: string): Chainable<any>;
  }
}

function loginViaAAD(username: string, password: string) {
  cy.visit('http://salat-test.hbt.de/')

  // Login to your AAD tenant.
  cy.origin(
      'login.microsoftonline.com',
      {
        args: {
          username,
        },
      },
      ({ username }) => {
        cy.get('input[type="email"]').type(username, {
          log: false,
        })
        cy.get('input[type="submit"]').click()

        cy.get('input[type="password"]').type(password, {
          log: false,
        })
        cy.get('input[type="submit"]').click()
        cy.get('#idBtn_Back').click()
      }
  )
  // Ensure Microsoft has redirected us back to the sample app with our logged in user.
  cy.url().should('equal', 'http://salat-test.hbt.de/')
  cy.get('#welcome-div').should(
      'contain',
      `Welcome ${Cypress.env('aad_username')}!`
  )
}

Cypress.Commands.add('loginToAAD', (username: string, password: string) => {
  const log = Cypress.log({
    displayName: 'Azure Active Directory Login',
    message: [`🔐 Authenticating | ${username}`],
    autoEnd: false,
  })
  log.snapshot('before')

  loginViaAAD(username, password)

  log.snapshot('after')
  log.end()
})