describe('template spec', () => {
  // beforeEach(() => {
  //   cy.origin(`https://login.microsoftonline.com/`, () => {
  //     cy.visit('/');
  //     cy.get('[type="email"]').type(`${Cypress.env('aad_username')}{enter}`);
  //     cy.get('[type="password"]').type(`${Cypress.env('aad_password')}{enter}`);
  //     cy.get('[type="submit"]').type('{enter}');
  //   });
  // })

  it('call ', () => {
    Cypress.on('uncaught:exception', (err, runnable) => {
      // returning false here prevents Cypress from
      // failing the test
      return false
    })
    cy.visit(
        {
          method: 'GET',
          url: '/do/ShowDailyReport',
          headers: {
            'user': 'tt'
          }
        })
  })
})

it('create report ', () => {
  Cypress.on('uncaught:exception', (err, runnable) => {
    // returning false here prevents Cypress from
    // failing the test
    return false
  })
  cy.visit(
      {
        method: 'GET',
        url: '/do/ShowDailyReport',
        headers: {
          'user': 'tt'
        }
      })
  const timestamp = Date.now();
  cy.get('.createnewreport').click();
  cy.url().should('contains', '/do/CreateDailyReport');
  cy.get('tr:nth-child(6) select:nth-child(1)').select('01');
  cy.url().should('contains', '/do/StoreDailyReport');
  cy.get('textarea').click();
  cy.get('textarea').type('test:'+timestamp);
  cy.get('[name="save"]').click();
  cy.get('[name="comment"]').last().should('have.text', 'test:'+timestamp);
  cy.get('.timereports tr.timereport .function-delete').last().click();
  cy.get('[name="comment"]').last().should('not.have.text', 'test:'+timestamp);

})