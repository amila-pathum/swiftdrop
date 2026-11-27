import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import AssistantChat from './AssistantChat'
import { apiClient } from '../services/apiClient'

vi.mock('../services/apiClient', () => ({ apiClient: { post: vi.fn() } }))

it('keeps the conversation when minimized and sends completed turns with a follow-up', async () => {
  apiClient.post.mockResolvedValueOnce({ reply: 'What is the parcel weight?' })
    .mockRejectedValueOnce(new Error()).mockResolvedValueOnce({ reply: 'Which parcel size?' })
  render(<AssistantChat />)
  fireEvent.click(screen.getByRole('button', { name: 'Ask assistant' }))
  fireEvent.click(screen.getByRole('button', { name: 'Estimate delivery fee' }))
  await screen.findByText('What is the parcel weight?')
  fireEvent.click(screen.getByRole('button', { name: 'Minimize assistant' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Ask assistant' }))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '2 kg' } })
  fireEvent.click(screen.getByRole('button', { name: 'Send message' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Try again' }))
  await screen.findByText('Which parcel size?')
  expect(screen.getAllByText('2 kg')).toHaveLength(1)
  expect(apiClient.post).toHaveBeenLastCalledWith('/api/ai/chat', {
    message: '2 kg',
    history: [{ message: 'Help me estimate a delivery fee.', reply: 'What is the parcel weight?' }],
  }, expect.objectContaining({ auth: true }))
})
