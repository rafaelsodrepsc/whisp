import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MessageArea } from './message-area';

describe('MessageArea', () => {
  let component: MessageArea;
  let fixture: ComponentFixture<MessageArea>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageArea],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageArea);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
